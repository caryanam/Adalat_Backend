package com.adalat.service.ai;

import com.adalat.dto.ai.*;
import com.adalat.entity.Customer;
import com.adalat.entity.LegalIntakeSession;
import com.adalat.enums.IntakeStatus;
import com.adalat.enums.Role;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LegalIntakeMessageRepository;
import com.adalat.repository.LegalIntakeSessionRepository;
import com.adalat.service.ai.engine.AntiLoopEngine;
import com.adalat.service.ai.engine.CompletionEngine;
import com.adalat.service.ai.engine.FactMergeService;
import com.adalat.service.ai.tracing.LegalAssistantDiagnosticTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LegalAssistantMultiFlowDiagnosticTest {

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
    private LegalAssistantDiagnosticTracer tracer = new LegalAssistantDiagnosticTracer();

    @InjectMocks
    private LegalConversationOrchestratorImpl orchestrator;

    private Customer testCustomer;
    private LegalIntakeSession activeSession;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .customerId(100L)
                .fullName("Test User")
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();

        activeSession = LegalIntakeSession.builder()
                .id(1L)
                .customer(testCustomer)
                .status(IntakeStatus.ACTIVE)
                .questionCount(0)
                .intakeComplete(false)
                .collectedFacts(new HashMap<>())
                .askedFactKeys(new HashSet<>())
                .askedQuestions(new ArrayList<>())
                .build();
    }

    @RepeatedTest(5)
    @DisplayName("Flow 1 (5x): 'hi' -> 'I need lawyer'")
    void testFlow1GreetingThenLawyerRequest() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Turn 1: "hi"
        CustomerMessageRequestDTO req1 = new CustomerMessageRequestDTO("hi", null);
        LegalIntakeResponseDTO resp1 = orchestrator.processCustomerMessage(100L, 1L, req1);

        assertNotNull(resp1);
        assertEquals(IntakeStatus.ACTIVE, resp1.getStatus());
        assertTrue(resp1.getAssistantMessage().toLowerCase().contains("hello") || resp1.getAssistantMessage().toLowerCase().contains("adalat"));
        assertEquals(0, activeSession.getQuestionCount());

        // Turn 2: "I need lawyer"
        CustomerMessageRequestDTO req2 = new CustomerMessageRequestDTO("I need lawyer", null);
        LegalIntakeResponseDTO resp2 = orchestrator.processCustomerMessage(100L, 1L, req2);

        assertNotNull(resp2);
        assertEquals(IntakeStatus.ACTIVE, resp2.getStatus());
        assertTrue(resp2.getAssistantMessage().contains("advocate") || resp2.getAssistantMessage().contains("lawyer"));
    }

    @RepeatedTest(5)
    @DisplayName("Flow 2 (5x): Vehicle theft + FIR + later accident")
    void testFlow2VehicleTheftFirAccident() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Turn 1
        CustomerMessageRequestDTO req1 = new CustomerMessageRequestDTO("mazi gaadi ek week adhi kharadi madhun chorila geli, mi tya sathi FIR pn keli, ata kal tya gadine ekala accident kela ahe, ata mi kay karu. ti case mazya vr honar ka", null);
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

        // Turn 2
        CustomerMessageRequestDTO req2 = new CustomerMessageRequestDTO("mazya kade gaadi chori zali ya sathich FIR copy ahe", null);
        AiIntakeResponseDTO aiResp2 = AiIntakeResponseDTO.builder()
                .assistantMessage("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("fir_copy_available", true))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp2);
        LegalIntakeResponseDTO resp2 = orchestrator.processCustomerMessage(100L, 1L, req2);

        assertNotEquals("या घटनेबद्दल तुमच्याकडे बिल, पावती, मेसेज किंवा इतर काही पुरावा किंवा अधिक माहिती आहे का?", resp2.getAssistantMessage());
    }

    @RepeatedTest(5)
    @DisplayName("Flow 3 (5x): Evidence already provided")
    void testFlow3EvidenceAlreadyProvided() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Turn 1
        CustomerMessageRequestDTO req1 = new CustomerMessageRequestDTO("I live in Mumbai. My employer hasn't paid my 50000 salary for 2 months. I have WhatsApp chats and offer letter.", null);
        AiIntakeResponseDTO aiResp1 = AiIntakeResponseDTO.builder()
                .assistantMessage("Do you have any WhatsApp evidence or documents?")
                .primaryCategory("EMPLOYMENT_SALARY")
                .newFacts(Map.of("core_problem", "unpaid salary", "pending_months", "2 months", "monthly_salary", "50000", "whatsapp_evidence", "chats available", "offer_letter", "available"))
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("Do you have any WhatsApp evidence or documents?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp1);
        LegalIntakeResponseDTO resp1 = orchestrator.processCustomerMessage(100L, 1L, req1);

        // Turn 2
        CustomerMessageRequestDTO req2 = new CustomerMessageRequestDTO("what should I do next?", null);
        AiIntakeResponseDTO aiResp2 = AiIntakeResponseDTO.builder()
                .assistantMessage("Do you have evidence?")
                .primaryCategory("EMPLOYMENT_SALARY")
                .newFacts(Map.of())
                .nextQuestionFactKey("evidence_status")
                .nextQuestion("Do you have evidence?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp2);
        LegalIntakeResponseDTO resp2 = orchestrator.processCustomerMessage(100L, 1L, req2);

        // Evidence question must be rejected and English guidance provided
        assertFalse(resp2.getAssistantMessage().contains("Do you have evidence?"));
        assertTrue(resp2.getAssistantMessage().toLowerCase().contains("evidence") || resp2.getAssistantMessage().toLowerCase().contains("keep") || resp2.getAssistantMessage().toLowerCase().contains("desired") || resp2.getAssistantMessage().toLowerCase().contains("outcome"));
    }

    @RepeatedTest(5)
    @DisplayName("Flow 4 (5x): Marathi flow")
    void testFlow4MarathiFlow() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CustomerMessageRequestDTO req = new CustomerMessageRequestDTO("माझे घरमालक मला विनाकारण घर खाली करायला सांगत आहेत, मी काय करू?", null);
        AiIntakeResponseDTO aiResp = AiIntakeResponseDTO.builder()
                .assistantMessage("तुमच्याकडे भाडे करार (Rent Agreement) आहे का?")
                .primaryCategory("TENANCY")
                .newFacts(Map.of("core_problem", "illegal eviction threat"))
                .nextQuestionFactKey("agreement_status")
                .nextQuestion("तुमच्याकडे भाडे करार (Rent Agreement) आहे का?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp);
        LegalIntakeResponseDTO resp = orchestrator.processCustomerMessage(100L, 1L, req);

        assertEquals("MARATHI", activeSession.getCollectedFacts().get("preferred_language"));
        assertTrue(resp.getAssistantMessage().contains("करार") || resp.getAssistantMessage().contains("Agreement") || resp.getAssistantMessage().contains("आहे"));
    }

    @RepeatedTest(5)
    @DisplayName("Flow 5 (5x): Hindi flow")
    void testFlow5HindiFlow() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CustomerMessageRequestDTO req = new CustomerMessageRequestDTO("मेरी दुकान से कल रात सामान चोरी हो गया है, मुझे क्या करना चाहिए?", null);
        AiIntakeResponseDTO aiResp = AiIntakeResponseDTO.builder()
                .assistantMessage("क्या आपने पुलिस स्टेशन में एफआईआर दर्ज कराई है?")
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("core_problem", "shop theft"))
                .nextQuestionFactKey("police_status")
                .nextQuestion("क्या आपने पुलिस स्टेशन में एफआईआर दर्ज कराई है?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp);
        LegalIntakeResponseDTO resp = orchestrator.processCustomerMessage(100L, 1L, req);

        assertEquals("HINDI", activeSession.getCollectedFacts().get("preferred_language"));
        assertTrue(resp.getAssistantMessage().contains("पुलिस") || resp.getAssistantMessage().contains("एफआईआर") || resp.getAssistantMessage().contains("क्या"));
    }

    @RepeatedTest(5)
    @DisplayName("Flow 6 (5x): English flow")
    void testFlow6EnglishFlow() {
        activeSession.setCollectedFacts(new HashMap<>());
        activeSession.setAskedFactKeys(new HashSet<>());
        activeSession.setAskedQuestions(new ArrayList<>());
        activeSession.setQuestionCount(0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(activeSession));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CustomerMessageRequestDTO req = new CustomerMessageRequestDTO("I bought a laptop for 60000 rupees from a store in Bangalore but it stopped working after 2 days.", null);
        AiIntakeResponseDTO aiResp = AiIntakeResponseDTO.builder()
                .assistantMessage("Do you have the purchase bill and warranty card?")
                .primaryCategory("CONSUMER")
                .newFacts(Map.of("core_problem", "defective laptop", "purchase_amount", "60000", "jurisdiction_city", "Bangalore"))
                .nextQuestionFactKey("purchase_evidence")
                .nextQuestion("Do you have the purchase bill and warranty card?")
                .intakeComplete(false)
                .build();
        when(aiProvider.processMessage(any())).thenReturn(aiResp);
        LegalIntakeResponseDTO resp = orchestrator.processCustomerMessage(100L, 1L, req);

        assertEquals("ENGLISH", activeSession.getCollectedFacts().get("preferred_language"));
        assertTrue(resp.getAssistantMessage().contains("bill") || resp.getAssistantMessage().contains("warranty") || resp.getAssistantMessage().contains("Do you"));
    }
}
