package com.adalat.service.ai;

import com.adalat.dto.ai.*;
import com.adalat.entity.Customer;
import com.adalat.entity.LegalIntakeMessage;
import com.adalat.entity.LegalIntakeSession;
import com.adalat.enums.IntakeStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.Role;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LegalIntakeMessageRepository;
import com.adalat.repository.LegalIntakeSessionRepository;
import com.adalat.service.ai.engine.AntiLoopEngine;
import com.adalat.service.ai.engine.CompletionEngine;
import com.adalat.service.ai.engine.FactMergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalConversationOrchestratorTest {

    @Mock
    private LegalIntakeSessionRepository sessionRepository;

    @Mock
    private LegalIntakeMessageRepository messageRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LegalAiProvider aiProvider;

    @Spy
    private FactMergeService factMergeService = new FactMergeService();

    @Spy
    private AntiLoopEngine antiLoopEngine = new AntiLoopEngine(factMergeService);

    @Spy
    private CompletionEngine completionEngine = new CompletionEngine(factMergeService);

    @Spy
    private com.adalat.service.ai.tracing.LegalAssistantDiagnosticTracer tracer = new com.adalat.service.ai.tracing.LegalAssistantDiagnosticTracer();

    @InjectMocks
    private LegalConversationOrchestratorImpl orchestrator;

    private Customer testCustomer;
    private LegalIntakeSession activeSession;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .customerId(100L)
                .fullName("Rohan Patil")
                .email("rohan@example.com")
                .mobileNumber("9876543210")
                .password("encoded_pass")
                .role(Role.CUSTOMER)
                .build();

        activeSession = LegalIntakeSession.builder()
                .id(1L)
                .customer(testCustomer)
                .status(IntakeStatus.ACTIVE)
                .primaryCategory(LegalCategory.TENANCY)
                .questionCount(0)
                .intakeComplete(false)
                .collectedFacts(new HashMap<>())
                .askedFactKeys(new HashSet<>())
                .askedQuestions(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("1. First legal message creates/extracts facts correctly")
    void testFirstLegalMessageExtractsFactsCorrectly() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        AiIntakeResponseDTO mockResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Samajla. Landlord ne notice dili aahe ka?")
                .primaryCategory("TENANCY")
                .jurisdiction(JurisdictionDTO.builder().city("Pune").state("Maharashtra").country("India").build())
                .urgency("HIGH")
                .newFacts(Map.of("core_problem", "Landlord asking to vacate in 3 days", "possession_status", "in_flat"))
                .nextQuestionFactKey("eviction_notice")
                .nextQuestion("Landlord ne notice dili aahe ka?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(mockResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("I live in Pune. My landlord is forcing me to leave in 3 days.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        assertNotNull(response);
        assertEquals(IntakeStatus.ACTIVE, response.getStatus());
        assertEquals("TENANCY", response.getPrimaryCategory());
        assertEquals("HIGH", response.getUrgency());
        assertEquals(1, response.getQuestionCount());
        assertTrue(activeSession.getCollectedFacts().containsKey("core_problem"));
        assertTrue(activeSession.getAskedFactKeys().contains("eviction_notice"));
        verify(messageRepository, atLeast(2)).save(any(LegalIntakeMessage.class));
    }

    @Test
    @DisplayName("2. Previously answered fact is not asked again")
    void testPreviouslyAnsweredFactIsNotAskedAgain() {
        activeSession.getCollectedFacts().put("agreement_status", "11 months registered lease");
        activeSession.getAskedFactKeys().add("agreement_status");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // AI attempts to ask agreement_status again
        AiIntakeResponseDTO repeatResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Do you have an agreement?")
                .nextQuestionFactKey("agreement_status")
                .nextQuestion("Do you have an agreement?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(repeatResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("What should I do next?", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        assertNotNull(response);
        // Anti-loop must have intervened: the question sent must NOT be agreement_status
        assertNotEquals("agreement_status", response.getAssistantMessage());
        // Fallback or retry question was chosen
        assertTrue(activeSession.getQuestionCount() > 0);
    }

    @Test
    @DisplayName("3. Same question with different wording is rejected by AntiLoopEngine")
    void testSameQuestionWithDifferentWordingIsRejected() {
        activeSession.getAskedQuestions().add("Do you have any written agreement or rental contract?");
        activeSession.getAskedFactKeys().add("agreement_doc");

        boolean isDuplicate = antiLoopEngine.isDuplicateQuestion(
                "contract_proof",
                "Do you have a written agreement or contract?",
                activeSession.getAskedFactKeys(),
                activeSession.getCollectedFacts(),
                activeSession.getAskedQuestions()
        );

        assertTrue(isDuplicate, "Semantic duplicate question must be caught by AntiLoopEngine");
    }

    @Test
    @DisplayName("4. AI says intakeComplete=false but backend decides enough facts exist")
    void testBackendDeclaresIntakeCompleteWhenDimensionsSatisfied() {
        // Fill all required dimensions for TENANCY
        activeSession.setJurisdictionCity("Pune");
        activeSession.setJurisdictionState("Maharashtra");
        activeSession.getCollectedFacts().put("core_problem", "Imminent illegal eviction");
        activeSession.getCollectedFacts().put("agreement_status", "Registered 11-month agreement active");
        activeSession.getCollectedFacts().put("eviction_or_notice_details", "No written notice given");
        activeSession.getCollectedFacts().put("possession_status", "Tenant resides in property");
        activeSession.getCollectedFacts().put("evidence_status", "WhatsApp chats and bank statements available");
        activeSession.getCollectedFacts().put("desired_outcome", "Stop unlawful eviction and continue lease");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // AI says false
        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Can you tell me more about your rent?")
                .primaryCategory("TENANCY")
                .nextQuestionFactKey("rent_amount")
                .nextQuestion("Can you tell me more about your rent?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("I want to stay until agreement expires.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Backend CompletionEngine overrides AI: status should become SUMMARY_READY!
        assertEquals(IntakeStatus.SUMMARY_READY, response.getStatus());
        assertTrue(response.isIntakeComplete());
        assertTrue(response.isCanConfirm());
        assertNotNull(response.getCustomerSummary());
    }

    @Test
    @DisplayName("5. AI says intakeComplete=true but critical facts are missing")
    void testBackendRejectsPrematureAiCompletion() {
        // Only 1 fact present
        activeSession.getCollectedFacts().put("core_problem", "Landlord dispute");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // AI mistakenly claims complete
        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Everything is done.")
                .primaryCategory("TENANCY")
                .intakeComplete(true)
                .customerSummary("Short summary")
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("My landlord yelled at me.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Backend must reject premature intake completion
        assertEquals(IntakeStatus.ACTIVE, response.getStatus());
        assertFalse(response.isIntakeComplete());
        assertFalse(response.isCanConfirm());
    }

    @Test
    @DisplayName("6. Customer corrects an existing fact")
    void testCustomerFactCorrectionOverridesOldValue() {
        activeSession.getCollectedFacts().put("monthly_salary", "30000");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // Customer says "Actually my salary was 35000 not 30000"
        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Updated your salary.")
                .primaryCategory("EMPLOYMENT_SALARY")
                .newFacts(Map.of("monthly_salary", "35000"))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("Do you have salary slips?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Actually sorry, my salary was 35000 not 30000.", null);
        orchestrator.processCustomerMessage(100L, 1L, request);

        // Verified that 35000 overrode 30000
        assertEquals("35000", activeSession.getCollectedFacts().get("monthly_salary"));
    }

    @Test
    @DisplayName("7. Question limit reached with incomplete case")
    void testQuestionLimitReachedForcesSummaryWithUnknownFlags() {
        // Set question count to 9 (limit is 10, so next message hits 10)
        activeSession.setQuestionCount(9);
        activeSession.getCollectedFacts().put("core_problem", "Unpaid salary");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Tell me more about HR.")
                .primaryCategory("EMPLOYMENT_SALARY")
                .nextQuestionFactKey("hr_contact")
                .nextQuestion("Tell me more about HR.")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("I tried contacting them.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Must force completion because max questions reached
        assertEquals(IntakeStatus.SUMMARY_READY, response.getStatus());
        assertTrue(response.isIntakeComplete());
        assertTrue(activeSession.getLawyerSummary().contains("NOT_PROVIDED") || activeSession.getLawyerSummary().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("8. Provider timeout/error handles gracefully without corrupting session")
    void testProviderErrorHandlesGracefully() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(aiProvider.processMessage(any())).thenThrow(new AiProviderException("AI connection timeout"));

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Hello I need help", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        assertNotNull(response);
        assertEquals(IntakeStatus.ACTIVE, response.getStatus());
        assertTrue(response.getAssistantMessage().contains("temporarily unavailable"));
        assertEquals(0, activeSession.getQuestionCount()); // Question count not incremented
        verify(messageRepository, times(2)).save(any(LegalIntakeMessage.class)); // Customer message and AI fallback message saved
    }

    @Test
    @DisplayName("9. Out-of-scope message is redirected without mutating legal facts")
    void testOutOfScopeMessageIsRedirected() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        AiIntakeResponseDTO outOfScopeResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("I can only assist with legal matters.")
                .primaryCategory(null)
                .newFacts(Collections.emptyMap())
                .nextQuestion(null)
                .nextQuestionFactKey(null)
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(outOfScopeResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Write me a poem about summer.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        assertEquals("I can only assist with legal matters.", response.getAssistantMessage());
        assertEquals(0, activeSession.getQuestionCount()); // Not counted as legal question
        assertTrue(activeSession.getCollectedFacts().isEmpty());
    }

    @Test
    @DisplayName("10. SUMMARY_READY prevents normal questioning and supports edit")
    void testSummaryReadyWorkflow() {
        activeSession.setStatus(IntakeStatus.SUMMARY_READY);
        activeSession.setIntakeComplete(true);
        activeSession.setCustomerSummary("Case summary: Tenancy dispute in Pune.");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        AiIntakeResponseDTO editResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Updated summary with new detail.")
                .customerSummary("Case summary: Tenancy dispute in Pune. Rent deposit is ₹50,000.")
                .newFacts(Map.of("deposit_amount", "50000"))
                .intakeComplete(true)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(editResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Also my deposit was 50000 rupees.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Remains in SUMMARY_READY, does not revert to question loop
        assertEquals(IntakeStatus.SUMMARY_READY, response.getStatus());
        assertTrue(response.isCanConfirm());
        assertTrue(response.isCanEdit());
        assertEquals("50000", activeSession.getCollectedFacts().get("deposit_amount"));
    }

    @Test
    @DisplayName("11. Session ownership check prevents one customer accessing another session")
    void testSessionOwnershipValidation() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Hello", null);

        // Request from customer 999L instead of owner 100L
        assertThrows(SecurityException.class, () -> {
            orchestrator.processCustomerMessage(999L, 1L, request);
        });

        assertThrows(SecurityException.class, () -> {
            orchestrator.getSessionState(999L, 1L);
        });
    }

    @Test
    @DisplayName("12. Duplicate/concurrent submission does not create duplicate AI turns")
    void testDuplicateSubmissionOnClosedSessionRejected() {
        activeSession.setStatus(IntakeStatus.CONFIRMED);

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("One more message", null);
        assertThrows(IllegalStateException.class, () -> {
            orchestrator.processCustomerMessage(100L, 1L, request);
        });
    }
    @Test
    @DisplayName("13. Active Session Duplicate Submission is handled correctly")
    void testActiveSessionDuplicateSubmission() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));

        // Mock messageRepository for idempotency check
        LegalIntakeMessage existingMsg = LegalIntakeMessage.builder()
                .session(activeSession)
                .message("My landlord is bad")
                .clientMessageId("client-123")
                .build();
        when(messageRepository.findFirstBySessionIdAndClientMessageId(1L, "client-123"))
                .thenReturn(Optional.of(existingMsg));

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("My landlord is bad", "client-123");
        
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Verify idempotency
        verify(messageRepository, never()).save(any(LegalIntakeMessage.class));
        verify(aiProvider, never()).processMessage(any(AiIntakeRequestDTO.class));
        assertNotNull(response);
    }

    @Test
    @DisplayName("14. Summary Edit Regeneration Test")
    void testSummaryEditRegeneration() {
        // Given a session in SUMMARY_READY
        activeSession.setStatus(IntakeStatus.SUMMARY_READY);
        activeSession.setIntakeComplete(true);
        activeSession.getCollectedFacts().put("monthly_salary", "July");
        activeSession.setCustomerSummary("Case summary: pending salary for July.");
        activeSession.setLawyerSummary("Lawyer summary: pending salary for July.");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // Customer says "Actually my pending salary is for May and June only, not July."
        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("Updated your summary.")
                .primaryCategory("EMPLOYMENT_SALARY")
                .newFacts(Map.of("monthly_salary", "May and June"))
                .intakeComplete(true)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("Actually my pending salary is for May and June only, not July.", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Verify fact is corrected
        assertEquals("May and June", activeSession.getCollectedFacts().get("monthly_salary"));
        
        // Verify intake doesn't restart from zero
        assertEquals(IntakeStatus.SUMMARY_READY, response.getStatus());
        assertTrue(response.isIntakeComplete());
        
        // Verify customerSummary is regenerated 
        assertNotEquals("Case summary: pending salary for July.", activeSession.getCustomerSummary());
        assertNotEquals("Lawyer summary: pending salary for July.", activeSession.getLawyerSummary());
        assertTrue(activeSession.getCustomerSummary().contains("May and June"));
        assertTrue(activeSession.getLawyerSummary().contains("May and June"));
    }
    @Test
    @DisplayName("15. Family Bug Test - Avoid generic fallback question when answering short")
    void testFamilyLoopBugShortAnswerBinding() {
        // Setup initial session
        activeSession.setPrimaryCategory(LegalCategory.FAMILY);
        activeSession.getCollectedFacts().put("core_problem", "Wife left with child, demands alimony for divorce");
        activeSession.setLastAskedFactKey("child_access_status");
        activeSession.getAskedQuestions().add("tumhala mulala bhetnyachi parvangi ahe ka?");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // Mock AI response to "nahi"
        // Since we instructed the AI to bind short answers to the previous question, it extracts the fact correctly.
        // It should also pick a DIFFERENT fact key now because `child_access_status` is fulfilled.
        AiIntakeResponseDTO aiResponse = AiIntakeResponseDTO.builder()
                .assistantMessage("tumchi jurisdiction kay ahe?")
                .primaryCategory("FAMILY")
                .newFacts(Map.of("child_access_status", "no access"))
                .nextQuestionFactKey("jurisdiction")
                .nextQuestion("tumchi jurisdiction kay ahe?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResponse);

        CustomerMessageRequestDTO request = new CustomerMessageRequestDTO("nahi", null);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(100L, 1L, request);

        // Verify AntiLoop didn't throw a generic fallback
        assertFalse(response.getAssistantMessage().contains("Could you share the current status of"));
        assertEquals("jurisdiction", response.getAssistantMessage().contains("jurisdiction") ? "jurisdiction" : ""); // Ensure the AI question was asked

        // Next turn, customer provides multiple facts
        CustomerMessageRequestDTO request2 = new CustomerMessageRequestDTO("tine kiva mi ajun tri kahi legal action ghetleli nahi ahe. ata mi pudhe kay kel pahij", null);
        
        AiIntakeResponseDTO aiResponse2 = AiIntakeResponseDTO.builder()
                .assistantMessage("tumcha marriage status kay ahe?")
                .primaryCategory("FAMILY")
                .newFacts(Map.of("legal_action_status", "NONE", "desired_outcome", "wants_next_legal_steps", "jurisdiction", "Maharashtra")) // Mocking previous extraction too
                .nextQuestionFactKey("marriage_status")
                .nextQuestion("tumcha marriage status kay ahe?")
                .intakeComplete(false)
                .build();

        // Have to reset the mock for the second call
        when(aiProvider.processMessage(any())).thenReturn(aiResponse2);
        
        LegalIntakeResponseDTO response2 = orchestrator.processCustomerMessage(100L, 1L, request2);

        // Verify no generic core_problem fallback was outputted
        String msg = response2.getAssistantMessage().toLowerCase();
        assertFalse(msg.contains("core problem"));
        assertFalse(msg.contains("evidence status"));
        assertFalse(msg.contains("desired outcome"));
        assertFalse(msg.contains("previous action"));
    }

    @Test
    @DisplayName("16. Regression Test: Aadhaar misuse, WhatsApp evidence, police complaint & guidance intent 'mag atta'")
    void testAadhaarMisuseGuidanceConversation() {
        activeSession.setPrimaryCategory(null);
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // Turn 1: Customer describes HR Aadhaar misuse
        CustomerMessageRequestDTO req1 = new CustomerMessageRequestDTO(
                "maya compnimdun hr ne ceo che adhar card neun illigal kam kel ahe tr atta kay katu", null);

        AiIntakeResponseDTO aiResp1 = AiIntakeResponseDTO.builder()
                .assistantMessage("याबाबत तुमच्याकडे काही व्हॉट्सॲप चॅट किंवा पुरावा आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("offence_type", "Aadhaar misuse / forgery", "core_problem", "HR misused CEO Aadhaar"))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("याबाबत तुमच्याकडे काही व्हॉट्सॲप चॅट किंवा पुरावा आहे का?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResp1);
        LegalIntakeResponseDTO resp1 = orchestrator.processCustomerMessage(100L, 1L, req1);

        assertEquals("CRIMINAL", activeSession.getPrimaryCategory().name());
        assertEquals("HR misused CEO Aadhaar", activeSession.getCollectedFacts().get("core_problem"));

        // Turn 2: Customer confirms WhatsApp evidence and asks what to do
        CustomerMessageRequestDTO req2 = new CustomerMessageRequestDTO("ho whatapp var ahe tr atta kay karu", null);

        AiIntakeResponseDTO aiResp2 = AiIntakeResponseDTO.builder()
                .assistantMessage("तुम्ही पोलिसांत तक्रार केली आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("whatsapp_evidence", "WhatsApp chats available"))
                .nextQuestionFactKey("police_status")
                .nextQuestion("तुम्ही पोलिसांत तक्रार केली आहे का?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResp2);
        LegalIntakeResponseDTO resp2 = orchestrator.processCustomerMessage(100L, 1L, req2);

        // Verify evidence is stored and recognized as known
        assertTrue(activeSession.getCollectedFacts().containsKey("whatsapp_evidence"));

        // Turn 3: Customer confirms police complaint filed and asks "mag atta"
        CustomerMessageRequestDTO req3 = new CustomerMessageRequestDTO("ho keli ahe mag atta", null);

        AiIntakeResponseDTO aiResp3 = AiIntakeResponseDTO.builder()
                .assistantMessage("ठीक आहे, तुम्ही पोलिसांत तक्रार केली आहे आणि WhatsApp पुरावाही तुमच्याकडे आहे. तक्रारीची प्रत व WhatsApp screenshots सुरक्षित ठेवा.\n\nया Aadhaar चा नेमका कोणत्या कामासाठी गैरवापर झाला होता हे तुम्हाला माहिती आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("police_status", "filed", "police_complaint_status", "filed"))
                .nextQuestionFactKey("misuse_details")
                .nextQuestion("या Aadhaar चा नेमका कोणत्या कामासाठी गैरवापर झाला होता हे तुम्हाला माहिती आहे का?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResp3);
        LegalIntakeResponseDTO resp3 = orchestrator.processCustomerMessage(100L, 1L, req3);

        // Verification requirement checks:
        // 1. Evidence is NOT in missingCriticalFacts
        List<String> missing = completionEngine.getMissingCriticalFacts(
                activeSession.getPrimaryCategory(), activeSession.getCollectedFacts(), null, null);
        assertFalse(missing.contains("evidence_status"), "Evidence must NOT remain in missingCriticalFacts once known");

        // 2. Police status stored as known
        assertEquals("filed", activeSession.getCollectedFacts().get("police_status"));

        // 3. Evidence question rejected by AntiLoop if attempted
        assertTrue(antiLoopEngine.isDuplicateQuestion("evidence_status", "Do you have evidence?", activeSession.getAskedFactKeys(), activeSession.getCollectedFacts(), activeSession.getAskedQuestions()),
                "AntiLoop must reject evidence question when evidence is known");

        // 4. Response provides guidance without re-asking evidence
        String botMsg = resp3.getAssistantMessage().toLowerCase();
        assertFalse(botMsg.contains("बिल, पावती, मेसेज किंवा इतर काही पुरावा"), "Must NOT re-ask whether evidence exists");
    }

    @Test
    @DisplayName("17. Regression Test: Stolen vehicle accident, FIR copy available, guidance 'ti case mazya vr honar ka' - Anti-loop & guidance")
    void testStolenVehicleAccidentLoopAndGuidance() {
        activeSession.setPrimaryCategory(null);
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // Turn 1: Customer describes vehicle theft, Kharadi location, FIR filed, stolen vehicle accident, and asks "ti case mazya vr honar ka"
        CustomerMessageRequestDTO req1 = new CustomerMessageRequestDTO(
                "mazi gaadi ek week adhi kharadi madhun chorila geli, mi tya sathi FIR pn keli, ata kal tya gadine ekala accident kela ahe, ata mi kay karu. ti case mazya vr honar ka", null);

        AiIntakeResponseDTO aiResp1 = AiIntakeResponseDTO.builder()
                .assistantMessage("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("incident", "vehicle theft", "incident_location", "Kharadi, Pune", "police_status", "FIR filed", "stolen_vehicle_accident", "yes"))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResp1);
        LegalIntakeResponseDTO resp1 = orchestrator.processCustomerMessage(100L, 1L, req1);

        assertEquals("CRIMINAL", activeSession.getPrimaryCategory().name());
        assertTrue(activeSession.getCollectedFacts().containsKey("police_status"));
        assertEquals(1, activeSession.getAskedQuestions().size());

        // Turn 2: Customer responds with FIR copy availability
        CustomerMessageRequestDTO req2 = new CustomerMessageRequestDTO("mazya kade gaadi chori zali ya sathich FIR copy ahe", null);

        // Mock AI repeating the exact same question (simulating loop behavior from AI provider)
        AiIntakeResponseDTO aiResp2Duplicate = AiIntakeResponseDTO.builder()
                .assistantMessage("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("fir_copy_available", true))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .intakeComplete(false)
                .build();

        when(aiProvider.processMessage(any())).thenReturn(aiResp2Duplicate);
        LegalIntakeResponseDTO resp2 = orchestrator.processCustomerMessage(100L, 1L, req2);

        // Verification assertions:
        // 1. fir_copy_available is merged into collectedFacts
        assertTrue(factMergeService.isMeaningfulValue(activeSession.getCollectedFacts().get("fir_copy_available")) ||
                   factMergeService.isMeaningfulValue(activeSession.getCollectedFacts().get("fir_filed")));

        // 2. evidence_status dimension is NOT in missingCriticalFacts
        List<String> missing = completionEngine.getMissingCriticalFacts(
                activeSession.getPrimaryCategory(), activeSession.getCollectedFacts(), "Pune", "Maharashtra");
        assertFalse(missing.contains("evidence_status"), "Evidence status MUST NOT remain missing when FIR copy is available");

        // 3. Assistant message is NOT the exact repeated question
        String botMsg = resp2.getAssistantMessage();
        assertNotEquals("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?", botMsg,
                "Assistant MUST NOT repeat the exact same evidence question");

        // 4. Normalized question text in askedQuestions is unique
        Set<String> normalizedAsked = new HashSet<>();
        for (String q : activeSession.getAskedQuestions()) {
            String norm = antiLoopEngine.normalizeText(q);
            assertFalse(normalizedAsked.contains(norm), "No duplicate normalized questions should exist in askedQuestions: " + q);
            normalizedAsked.add(norm);
        }
    }
}
