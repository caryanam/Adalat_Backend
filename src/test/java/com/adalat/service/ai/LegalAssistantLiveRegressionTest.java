package com.adalat.service.ai;

import com.adalat.dto.ai.*;
import com.adalat.entity.Customer;
import com.adalat.entity.LegalIntakeSession;
import com.adalat.enums.IntakeStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.LegalIntakeMessageRepository;
import com.adalat.repository.LegalIntakeSessionRepository;
import com.adalat.service.ai.engine.AntiLoopEngine;
import com.adalat.service.ai.engine.CompletionEngine;
import com.adalat.service.ai.engine.FactMergeService;
import com.adalat.service.ai.tracing.LegalAssistantDiagnosticTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LegalAssistantLiveRegressionTest {

    @Mock
    private LegalIntakeSessionRepository sessionRepository;

    @Mock
    private LegalIntakeMessageRepository messageRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LegalAiProvider aiProvider;

    @Mock
    private LawyerMatchingService lawyerMatchingService;

    @Mock
    private ConsultationRequestRepository consultationRequestRepository;

    @Mock
    private LawyerRepository lawyerRepository;

    @Mock
    private LegalAssistantDiagnosticTracer tracer;

    private FactMergeService factMergeService;
    private AntiLoopEngine antiLoopEngine;
    private CompletionEngine completionEngine;
    private LegalConversationOrchestratorImpl orchestrator;

    private Customer customer;
    private LegalIntakeSession session;

    @BeforeEach
    void setUp() {
        factMergeService = new FactMergeService();
        antiLoopEngine = new AntiLoopEngine(factMergeService);
        completionEngine = new CompletionEngine(factMergeService);

        orchestrator = new LegalConversationOrchestratorImpl(
                sessionRepository,
                messageRepository,
                customerRepository,
                aiProvider,
                factMergeService,
                antiLoopEngine,
                completionEngine,
                lawyerMatchingService,
                consultationRequestRepository,
                lawyerRepository,
                tracer
        );

        customer = new Customer();
        customer.setCustomerId(100L);
        customer.setFullName("Test Customer");

        session = new LegalIntakeSession();
        session.setId(1L);
        session.setCustomer(customer);
        session.setStatus(IntakeStatus.ACTIVE);
        session.setPrimaryCategory(LegalCategory.CRIMINAL);
        session.setQuestionCount(0);
        session.setCollectedFacts(new HashMap<>());
        session.setAskedFactKeys(new HashSet<>());
        session.setAskedQuestions(new ArrayList<>());

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testExactLiveRegressionFlow() {
        // TURN 1: Initial Threat Description
        String turn1Text = "maza mitra kal ratri daru piun call karun shivya deu lagla ani maraychya dhamkya det hota mala kahi kalat nahiye mi kay karu mi tyala bollo asa ka bolat ahes to aikun gheeyla tayar nahi shivya detoy ani thodya velane parat call karun dhamkya deu lagla";

        AiIntakeResponseDTO turn1AiResp = AiIntakeResponseDTO.builder()
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of(
                        "offence_type", "verbal abuse and death threats on phone call",
                        "accused_relationship", "friend",
                        "incident_time", "yesterday night"
                ))
                .nextQuestionFactKey("audio_recording_available")
                .nextQuestion("कॉलचे रेकॉर्डिंग किंवा काही मेसेज पुरावा म्हणून उपलब्ध आहे का?")
                .assistantMessage("ठीक आहे. धमकीच्या फोन कॉल्सबाबत त्वरित माहिती नोंदवली आहे. कॉलचे रेकॉर्डिंग किंवा मेसेज पुरावा आहे का?")
                .build();

        when(aiProvider.processMessage(any())).thenReturn(turn1AiResp);

        LegalIntakeResponseDTO res1 = orchestrator.processCustomerMessage(100L, 1L, CustomerMessageRequestDTO.builder().message(turn1Text).build());

        assertNotNull(res1);
        assertEquals(IntakeStatus.ACTIVE, session.getStatus());
        assertTrue(session.getCollectedFacts().containsKey("offence_type"));
        assertEquals("friend", session.getCollectedFacts().get("accused_relationship"));

        // TURN 2: Audio Recording & Police Complaint Status
        String turn2Text = "mazya kade call che audio recoding nahiye ani mi ajun takrakar keli nahiye polisanna ata kay karu tri kay as zalay mala";

        AiIntakeResponseDTO turn2AiResp = AiIntakeResponseDTO.builder()
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of(
                        "audio_recording_available", false,
                        "police_complaint_status", "not_filed"
                ))
                .nextQuestionFactKey("jurisdiction_city")
                .nextQuestion("ही घटना कोणत्या शहरातील किंवा भागातील आहे?")
                .assistantMessage("हे प्रकरण गंभीर आहे. तुम्ही जवळच्या पोलीस ठाण्यात NC किंवा तक्रार देऊ शकता. ही घटना कोणत्या शहरातील आहे?")
                .build();

        when(aiProvider.processMessage(any())).thenReturn(turn2AiResp);

        LegalIntakeResponseDTO res2 = orchestrator.processCustomerMessage(100L, 1L, CustomerMessageRequestDTO.builder().message(turn2Text).build());

        assertEquals(false, session.getCollectedFacts().get("audio_recording_available"));
        assertEquals("not_filed", session.getCollectedFacts().get("police_complaint_status"));

        // TURN 3: Location Details (Pune, Wanwadi)
        String turn3Text = "mi sadhya wanwadi pune madhe rahto";

        AiIntakeResponseDTO turn3AiResp = AiIntakeResponseDTO.builder()
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of(
                        "jurisdiction_city", "Pune",
                        "jurisdiction_area", "Wanwadi"
                ))
                .jurisdiction(JurisdictionDTO.builder().city("Pune").state("Maharashtra").build())
                .intakeComplete(true)
                .customerSummary("Case Category: Criminal Law\nLocation: Pune, Wanwadi\n\nKey Facts:\n- Offence: Threatening calls and abuse by friend\n- Recording: Not available\n- Police complaint: Not filed yet")
                .lawyerSummary("CRIMINAL INTAKE ASSESSMENT SUMMARY\nCategory: CRIMINAL\nJurisdiction: Pune")
                .build();

        when(aiProvider.processMessage(any())).thenReturn(turn3AiResp);

        LegalIntakeResponseDTO res3 = orchestrator.processCustomerMessage(100L, 1L, CustomerMessageRequestDTO.builder().message(turn3Text).build());

        assertEquals("Pune", session.getJurisdictionCity());
        assertEquals("Wanwadi", session.getCollectedFacts().get("jurisdiction_area"));

        // Verify next question is NOT generic bill/receipt fallback
        if (res3.getAssistantMessage() != null) {
            assertFalse(res3.getAssistantMessage().contains("बिल"), "Must not ask generic bill/receipt question");
            assertFalse(res3.getAssistantMessage().contains("पावती"), "Must not ask generic receipt question");
        }
        assertEquals(IntakeStatus.SUMMARY_READY, session.getStatus(), "Intake should transition to SUMMARY_READY");

        // TURN 4: META_CONTEXT_REQUEST ("bhai apla topic kay suru ahe 1?")
        Map<String, Object> factsBeforeMeta = new HashMap<>(session.getCollectedFacts());
        int qCountBeforeMeta = session.getQuestionCount();

        String turn4Text = "bhai apla topic kay suru ahe 1?";
        LegalIntakeResponseDTO res4 = orchestrator.processCustomerMessage(100L, 1L, CustomerMessageRequestDTO.builder().message(turn4Text).build());

        assertEquals(factsBeforeMeta, session.getCollectedFacts(), "Facts must remain unchanged on META_CONTEXT_REQUEST");
        assertEquals(qCountBeforeMeta, session.getQuestionCount(), "Question count must remain unchanged");
        assertEquals(IntakeStatus.SUMMARY_READY, session.getStatus(), "Session state must remain SUMMARY_READY");
        assertTrue(res4.getAssistantMessage().contains("बोलत आहोत"), "Response must briefly summarize context");

        // TURN 5: SUMMARY_CONFIRMATION ("yess" and variants)
        List<String> confVariants = List.of("yess", "yes", "ho", "barobar", "correct", "haan");

        for (String confText : confVariants) {
            LegalIntakeSession subSession = new LegalIntakeSession();
            subSession.setId(10L);
            subSession.setCustomer(customer);
            subSession.setStatus(IntakeStatus.SUMMARY_READY);
            subSession.setPrimaryCategory(LegalCategory.CRIMINAL);
            subSession.setQuestionCount(3);
            subSession.setCustomerSummary("Case Summary Ready");
            subSession.setCollectedFacts(new HashMap<>(factsBeforeMeta));

            when(sessionRepository.findById(10L)).thenReturn(Optional.of(subSession));
            when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(inv -> inv.getArgument(0));

            LegalIntakeResponseDTO confRes = orchestrator.processCustomerMessage(100L, 10L, CustomerMessageRequestDTO.builder().message(confText).build());

            assertFalse(subSession.getCollectedFacts().containsKey("evidenceAvailable"), "evidenceAvailable must NOT be added on confirmation: " + confText);
            assertTrue(subSession.isConfirmed(), "Session must be marked confirmed for: " + confText);
            assertEquals(IntakeStatus.AWAITING_NEXT_STEP, subSession.getStatus(), "Status must transition to AWAITING_NEXT_STEP for: " + confText);
            assertTrue(confRes.isNextActionRequired(), "nextActionRequired must be true");
            assertEquals(List.of("CONNECT_LAWYER", "AI_ONLY"), confRes.getAvailableActions());
            assertFalse(confRes.getAssistantMessage().contains("Case Summary Ready"), "Summary must NOT be re-rendered on confirmation");
        }
    }

    @Test
    void testSummaryCorrectionWorkflow() {
        LegalIntakeSession editSession = new LegalIntakeSession();
        editSession.setId(20L);
        editSession.setCustomer(customer);
        editSession.setStatus(IntakeStatus.SUMMARY_READY);
        editSession.setPrimaryCategory(LegalCategory.CRIMINAL);
        editSession.setQuestionCount(3);
        editSession.setCustomerSummary("Initial Summary");
        Map<String, Object> facts = new HashMap<>();
        facts.put("jurisdiction_city", "Pune");
        editSession.setCollectedFacts(facts);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(editSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(inv -> inv.getArgument(0));

        String correctionText = "barobar pan location Hadapsar hoti";

        AiIntakeResponseDTO correctionAiResp = AiIntakeResponseDTO.builder()
                .primaryCategory("CRIMINAL")
                .newFacts(Map.of("jurisdiction_city", "Hadapsar"))
                .customerSummary("Updated Summary: Hadapsar")
                .lawyerSummary("Updated Lawyer Summary")
                .build();

        when(aiProvider.processMessage(any())).thenReturn(correctionAiResp);

        LegalIntakeResponseDTO editRes = orchestrator.processCustomerMessage(100L, 20L, CustomerMessageRequestDTO.builder().message(correctionText).build());

        assertEquals("Hadapsar", editSession.getCollectedFacts().get("jurisdiction_city"), "Corrected fact should be updated");
        assertEquals(IntakeStatus.SUMMARY_READY, editSession.getStatus(), "Status should remain SUMMARY_READY for confirmation");
        assertEquals("Updated Summary: Hadapsar", editSession.getCustomerSummary());
    }

    @Test
    void testClarificationNeededFallback() {
        LegalIntakeSession clarifSession = new LegalIntakeSession();
        clarifSession.setId(30L);
        clarifSession.setCustomer(customer);
        clarifSession.setStatus(IntakeStatus.ACTIVE);
        clarifSession.setPrimaryCategory(LegalCategory.CRIMINAL);
        clarifSession.setQuestionCount(2);
        clarifSession.setLastAskedFactKey("police_status");
        Map<String, Object> facts = new HashMap<>();
        facts.put("incident", "vehicle theft");
        facts.put("preferred_language", "MARATHI");
        clarifSession.setCollectedFacts(facts);

        when(sessionRepository.findById(30L)).thenReturn(Optional.of(clarifSession));
        when(sessionRepository.save(any(LegalIntakeSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> factsBeforeGarbled = new HashMap<>(clarifSession.getCollectedFacts());

        // Send garbled message: "asdfghjkl"
        LegalIntakeResponseDTO resGarbled = orchestrator.processCustomerMessage(100L, 30L, CustomerMessageRequestDTO.builder().message("asdfghjkl").build());

        assertEquals(factsBeforeGarbled, clarifSession.getCollectedFacts(), "Facts must remain unchanged on garbled message");
        assertEquals(2, clarifSession.getQuestionCount(), "Question count must remain unchanged");
        assertEquals(IntakeStatus.ACTIVE, clarifSession.getStatus(), "Status must remain ACTIVE");
        assertEquals("police_status", clarifSession.getLastAskedFactKey(), "Previous context key must be preserved");
        assertTrue(resGarbled.getAssistantMessage().contains("स्पष्ट सांगाल का"), "Response must contain Marathi clarification message");

        // Test English garbled message
        clarifSession.getCollectedFacts().put("preferred_language", "ENGLISH");
        Map<String, Object> factsBeforeEngGarbled = new HashMap<>(clarifSession.getCollectedFacts());
        LegalIntakeResponseDTO resEngGarbled = orchestrator.processCustomerMessage(100L, 30L, CustomerMessageRequestDTO.builder().message("??????").build());
        assertEquals(factsBeforeEngGarbled, clarifSession.getCollectedFacts(), "Facts must remain unchanged on English garbled message");
        assertTrue(resEngGarbled.getAssistantMessage().contains("couldn't clearly understand"), "Response must contain English clarification message");
    }
}
