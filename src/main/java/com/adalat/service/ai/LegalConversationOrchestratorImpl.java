package com.adalat.service.ai;

import com.adalat.dto.ai.*;
import com.adalat.entity.Customer;
import com.adalat.entity.LegalIntakeMessage;
import com.adalat.entity.LegalIntakeSession;
import com.adalat.enums.IntakeStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.MessageType;
import com.adalat.enums.SenderType;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LegalIntakeMessageRepository;
import com.adalat.repository.LegalIntakeSessionRepository;
import com.adalat.service.ai.engine.AntiLoopEngine;
import com.adalat.service.ai.engine.CompletionEngine;
import com.adalat.service.ai.engine.FactMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Lawyer;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.LawyerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalConversationOrchestratorImpl implements LegalConversationOrchestrator {

    private final LegalIntakeSessionRepository sessionRepository;
    private final LegalIntakeMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final LegalAiProvider aiProvider;
    private final FactMergeService factMergeService;
    private final AntiLoopEngine antiLoopEngine;
    private final CompletionEngine completionEngine;
    private final LawyerMatchingService lawyerMatchingService;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final LawyerRepository lawyerRepository;
    private final com.adalat.service.ai.tracing.LegalAssistantDiagnosticTracer tracer;

    private static final Set<String> ALLOWED_URGENCY = Set.of("LOW", "NORMAL", "HIGH", "CRITICAL", "UNKNOWN");

    @Override
    @Transactional
    public LegalIntakeResponseDTO processCustomerMessage(Long customerId, Long sessionId, CustomerMessageRequestDTO request) {
        String userText = request != null && request.getMessage() != null ? request.getMessage().trim() : "";
        String clientMsgId = request != null && request.getClientMessageId() != null ? request.getClientMessageId().trim() : null;
        if (userText.isEmpty()) {
            throw new IllegalArgumentException("Customer message cannot be empty.");
        }

        // 1 & 2. Load session and customer
        LegalIntakeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal intake session not found with ID: " + sessionId));

        // 3. Verify session ownership
        if (!session.getCustomer().getCustomerId().equals(customerId)) {
            throw new SecurityException("Unauthorized access to legal intake session.");
        }

        // Capture facts before merge & detect language & intent
        Map<String, Object> factsBeforeMerge = new HashMap<>(session.getCollectedFacts());
        String detectedLang = detectLanguage(userText, session.getCollectedFacts());

        // 4. Verify status allows messages
        String lowerText = userText.toLowerCase();
        boolean isLawyerReq = lowerText.contains("lawyer") || lowerText.contains("vakeel") || lowerText.contains("vakil") || lowerText.contains("advocate") || lowerText.contains("connect");
        boolean isSummaryEdit = session.getStatus() == IntakeStatus.SUMMARY_READY;
        String detectedIntent = detectIntent(userText, session.getStatus(), isLawyerReq, isGuidanceRequested(userText), isSummaryEdit);

        if (session.getStatus() == IntakeStatus.CLOSED || session.getStatus() == IntakeStatus.ASSIGNED || session.getStatus() == IntakeStatus.LAWYER_MATCHING
                || (session.getStatus() == IntakeStatus.CONFIRMED && !isLawyerReq)) {
            throw new IllegalStateException("Intake session is already finalized. Further messages are not accepted.");
        }

        // Handle CLARIFICATION_NEEDED intent
        if ("CLARIFICATION_NEEDED".equals(detectedIntent)) {
            LegalIntakeMessage customerMsg = LegalIntakeMessage.builder()
                    .session(session)
                    .senderType(SenderType.CUSTOMER)
                    .messageType(MessageType.TEXT)
                    .message(userText)
                    .clientMessageId(clientMsgId)
                    .build();
            messageRepository.save(customerMsg);

            String clarificationMsg = getClarificationMessage(detectedLang);
            persistAiMessage(session, clarificationMsg, MessageType.TEXT);

            TurnDiagnosticTrace trace = TurnDiagnosticTrace.builder()
                    .sessionId(sessionId)
                    .detectedLanguage(detectedLang)
                    .detectedIntent(detectedIntent)
                    .sessionState(session.getStatus() + " (qCount=" + session.getQuestionCount() + ")")
                    .structuredFactsBeforeMerge(factsBeforeMerge)
                    .aiNewFacts(Map.of())
                    .structuredFactsAfterMerge(session.getCollectedFacts())
                    .missingCriticalFacts(completionEngine.getMissingCriticalFacts(session.getPrimaryCategory(), session.getCollectedFacts(), session.getJurisdictionCity(), session.getJurisdictionState()))
                    .aiNextQuestionFactKey(null)
                    .aiNextQuestion(null)
                    .antiLoopRejected(false)
                    .regenerationAttempted(false)
                    .deterministicFallbackUsed(false)
                    .finalPersistedQuestion(null)
                    .finalApiAssistantMessage(clarificationMsg)
                    .build();
            if (tracer != null) tracer.logTrace(trace);

            return buildFrontendResponse(session, clarificationMsg);
        }

        // Handle SUMMARY_CONFIRMATION intent on SUMMARY_READY status
        if ("SUMMARY_CONFIRMATION".equals(detectedIntent)) {
            LegalIntakeMessage customerMsg = LegalIntakeMessage.builder()
                    .session(session)
                    .senderType(SenderType.CUSTOMER)
                    .messageType(MessageType.TEXT)
                    .message(userText)
                    .clientMessageId(clientMsgId)
                    .build();
            messageRepository.save(customerMsg);

            return confirmSummary(customerId, sessionId);
        }

        // Handle META_CONTEXT_REQUEST intent
        if ("META_CONTEXT_REQUEST".equals(detectedIntent)) {
            LegalIntakeMessage customerMsg = LegalIntakeMessage.builder()
                    .session(session)
                    .senderType(SenderType.CUSTOMER)
                    .messageType(MessageType.TEXT)
                    .message(userText)
                    .clientMessageId(clientMsgId)
                    .build();
            messageRepository.save(customerMsg);

            String metaMsg = buildMetaContextResponse(session, detectedLang);
            persistAiMessage(session, metaMsg, MessageType.TEXT);

            TurnDiagnosticTrace trace = TurnDiagnosticTrace.builder()
                    .sessionId(sessionId)
                    .detectedLanguage(detectedLang)
                    .detectedIntent(detectedIntent)
                    .sessionState(session.getStatus() + " (qCount=" + session.getQuestionCount() + ")")
                    .structuredFactsBeforeMerge(factsBeforeMerge)
                    .aiNewFacts(Map.of())
                    .structuredFactsAfterMerge(session.getCollectedFacts())
                    .missingCriticalFacts(completionEngine.getMissingCriticalFacts(session.getPrimaryCategory(), session.getCollectedFacts(), session.getJurisdictionCity(), session.getJurisdictionState()))
                    .aiNextQuestionFactKey(null)
                    .aiNextQuestion(null)
                    .antiLoopRejected(false)
                    .regenerationAttempted(false)
                    .deterministicFallbackUsed(false)
                    .finalPersistedQuestion(null)
                    .finalApiAssistantMessage(metaMsg)
                    .build();
            if (tracer != null) tracer.logTrace(trace);

            return buildFrontendResponse(session, metaMsg);
        }

        // Check if customer in AWAITING_NEXT_STEP or AI_ONLY_COMPLETED or CONFIRMED is asking for a lawyer via text
        if ((session.getStatus() == IntakeStatus.AWAITING_NEXT_STEP || session.getStatus() == IntakeStatus.CONFIRMED || session.getStatus() == IntakeStatus.AI_ONLY_COMPLETED)
                && isLawyerReq) {
            log.info("Customer in status {} requested lawyer via text: '{}'", session.getStatus(), userText);
            return handleNextStepChoice(customerId, sessionId, "CONNECT_LAWYER");
        }

        // Handle LAWYER_REQUEST on ACTIVE status when core problem is not yet collected
        if (session.getStatus() == IntakeStatus.ACTIVE && isLawyerReq && !completionEngine.isIntakeComplete(session.getPrimaryCategory(), session.getCollectedFacts(), session.getJurisdictionCity(), session.getJurisdictionState(), session.getQuestionCount(), false)) {
            String lawyerAckMsg;
            if ("ENGLISH".equals(detectedLang)) {
                lawyerAckMsg = "I understand you need an advocate. To match you with the right lawyer and prepare your case summary, please briefly describe what happened.";
            } else if ("HINDI".equals(detectedLang)) {
                lawyerAckMsg = "मैं समझता हूँ कि आपको वकील की आवश्यकता है। आपको सही वकील से जोड़ने और आपका केस तैयार करने के लिए, कृपया संक्षेप में बताएं कि क्या हुआ था।";
            } else {
                lawyerAckMsg = "मला समजले की तुम्हाला ॲडव्होकेट हवे आहेत. तुमच्या प्रकरणासाठी योग्य वकील जोडण्यासाठी आणि केस तयार करण्यासाठी, कृपया थोडक्यात काय घडले ते सांगा.";
            }
            persistAiMessage(session, lawyerAckMsg, MessageType.TEXT);

            TurnDiagnosticTrace trace = TurnDiagnosticTrace.builder()
                    .sessionId(sessionId)
                    .detectedLanguage(detectedLang)
                    .detectedIntent(detectedIntent)
                    .sessionState(session.getStatus() + " (qCount=" + session.getQuestionCount() + ")")
                    .structuredFactsBeforeMerge(factsBeforeMerge)
                    .aiNewFacts(Map.of())
                    .structuredFactsAfterMerge(session.getCollectedFacts())
                    .missingCriticalFacts(completionEngine.getMissingCriticalFacts(session.getPrimaryCategory(), session.getCollectedFacts(), session.getJurisdictionCity(), session.getJurisdictionState()))
                    .aiNextQuestionFactKey(null)
                    .aiNextQuestion(null)
                    .antiLoopRejected(false)
                    .regenerationAttempted(false)
                    .deterministicFallbackUsed(false)
                    .finalPersistedQuestion(null)
                    .finalApiAssistantMessage(lawyerAckMsg)
                    .build();
            if (tracer != null) tracer.logTrace(trace);

            return buildFrontendResponse(session, lawyerAckMsg);
        }

        // Handle GREETING intent on turn 0/1
        if ("GREETING".equals(detectedIntent) && session.getQuestionCount() == 0) {
            String greetingMsg;
            if ("ENGLISH".equals(detectedLang)) {
                greetingMsg = "Hello! I am your Adalat AI Legal Assistant. Please describe your legal problem or what happened, and I will help organize your case for an advocate.";
            } else if ("HINDI".equals(detectedLang)) {
                greetingMsg = "नमस्ते! मैं आपका अदालत AI लीगल असिस्टेंट हूँ। कृपया अपनी कानूनी समस्या या घटना का विवरण बताएं, मैं आपके केस को वकील के लिए व्यवस्थित करने में मदद करूंगा।";
            } else {
                greetingMsg = "नमस्कार! मी तुमचा अदालत AI लीगल असिस्टंट आहे. कृपया तुमची कायदेशीर अडचण किंवा घडलेली घटना सांगा, मी वकिलांसाठी तुमचे प्रकरण व्यवस्थित मांडण्यात मदत करेन.";
            }
            persistAiMessage(session, greetingMsg, MessageType.TEXT);

            TurnDiagnosticTrace trace = TurnDiagnosticTrace.builder()
                    .sessionId(sessionId)
                    .detectedLanguage(detectedLang)
                    .detectedIntent(detectedIntent)
                    .sessionState(session.getStatus() + " (qCount=" + session.getQuestionCount() + ")")
                    .structuredFactsBeforeMerge(factsBeforeMerge)
                    .aiNewFacts(Map.of())
                    .structuredFactsAfterMerge(session.getCollectedFacts())
                    .missingCriticalFacts(completionEngine.getMissingCriticalFacts(session.getPrimaryCategory(), session.getCollectedFacts(), session.getJurisdictionCity(), session.getJurisdictionState()))
                    .aiNextQuestionFactKey(null)
                    .aiNextQuestion(null)
                    .antiLoopRejected(false)
                    .regenerationAttempted(false)
                    .deterministicFallbackUsed(false)
                    .finalPersistedQuestion(null)
                    .finalApiAssistantMessage(greetingMsg)
                    .build();
            if (tracer != null) tracer.logTrace(trace);

            return buildFrontendResponse(session, greetingMsg);
        }

        // 5. Idempotency Guard: Prevent duplicate submission if clientMessageId already processed
        if (clientMsgId != null && !clientMsgId.isBlank()) {
            Optional<LegalIntakeMessage> existing = messageRepository.findFirstBySessionIdAndClientMessageId(sessionId, clientMsgId);
            if (existing.isPresent()) {
                log.info("Idempotency hit for sessionId={} and clientMessageId='{}'. Returning current session state without duplicating.", sessionId, clientMsgId);
                return getSessionState(customerId, sessionId);
            }
        }
        // Also check if latest message in session is an exact duplicate from customer
        Optional<LegalIntakeMessage> lastMsgOpt = messageRepository.findFirstBySessionIdOrderByIdDesc(sessionId);
        if (lastMsgOpt.isPresent()) {
            LegalIntakeMessage lastMsg = lastMsgOpt.get();
            if (lastMsg.getSenderType() == SenderType.CUSTOMER && userText.equals(lastMsg.getMessage())) {
                log.info("Idempotency hit: sequential duplicate customer message in sessionId={}. Returning current state.", sessionId);
                return getSessionState(customerId, sessionId);
            }
        }

        // 6. Persist customer message first (guarantees message history is never lost)
        LegalIntakeMessage customerMsg = LegalIntakeMessage.builder()
                .session(session)
                .senderType(SenderType.CUSTOMER)
                .messageType(MessageType.TEXT)
                .message(userText)
                .clientMessageId(clientMsgId)
                .build();
        messageRepository.save(customerMsg);

        // 8. Build AI request
        AiIntakeRequestDTO aiRequest = buildAiRequest(session, userText, isSummaryEdit);

        // 9. Call AI Provider safely
        AiIntakeResponseDTO aiResponse;
        try {
            aiResponse = aiProvider.processMessage(aiRequest);
        } catch (Exception e) {
            log.warn("AI Provider failure for session {}: {}", sessionId, e.getMessage());
            String fallbackMsg = getAiFailureFallbackMessage(detectedLang);
            persistAiMessage(session, fallbackMsg, MessageType.TEXT);
            sessionRepository.save(session);
            return buildFrontendResponse(session, fallbackMsg);
        }

        // 10. Handle Out-Of-Scope query
        if (isOutOfScope(aiResponse)) {
            String redirectMsg = (aiResponse.getAssistantMessage() != null && !aiResponse.getAssistantMessage().isBlank())
                    ? aiResponse.getAssistantMessage()
                    : "I am an AI Legal Assistant for Adalat. I can only assist with legal matters. Please describe your legal issue so I can help.";

            persistAiMessage(session, redirectMsg, MessageType.TEXT);
            sessionRepository.save(session);

            return buildFrontendResponse(session, redirectMsg);
        }

        // 11. Merge new facts safely
        Map<String, Object> newFacts = (aiResponse != null && aiResponse.getNewFacts() != null) ? aiResponse.getNewFacts() : Map.of();
        Map<String, Object> updatedFacts = factMergeService.mergeFacts(
                session.getCollectedFacts(),
                newFacts,
                userText
        );
        updatedFacts.put("preferred_language", detectedLang);
        session.setCollectedFacts(updatedFacts);

        // 12. Validate and update category, jurisdiction and urgency
        updateSessionMetadata(session, aiResponse);

        // Diagnostic tracing holders
        boolean[] antiLoopRejectedHolder = new boolean[]{false};
        boolean[] regenerationAttemptedHolder = new boolean[]{false};
        boolean[] fallbackUsedHolder = new boolean[]{false};
        String[] finalPersistedQHolder = new String[]{null};

        // 13 & 14. Anti-loop & Completion checks
        if (isSummaryEdit) {
            aiResponse = handleSummaryEditWorkflow(session, aiResponse, userText);
        } else {
            aiResponse = handleStandardIntakeWorkflow(session, aiResponse, aiRequest, userText, detectedLang,
                    antiLoopRejectedHolder, regenerationAttemptedHolder, fallbackUsedHolder, finalPersistedQHolder);
        }

        // 17. Update session in DB
        LegalIntakeSession savedSession = sessionRepository.save(session);

        String finalAssistantMsg = session.getStatus() == IntakeStatus.SUMMARY_READY
                ? savedSession.getCustomerSummary()
                : (aiResponse != null ? aiResponse.getAssistantMessage() : null);

        // Emit diagnostic trace log
        TurnDiagnosticTrace trace = TurnDiagnosticTrace.builder()
                .sessionId(sessionId)
                .detectedLanguage(detectedLang)
                .detectedIntent(detectedIntent)
                .sessionState(savedSession.getStatus() + " (qCount=" + savedSession.getQuestionCount() + ")")
                .structuredFactsBeforeMerge(factsBeforeMerge)
                .aiNewFacts(aiResponse != null ? aiResponse.getNewFacts() : Map.of())
                .structuredFactsAfterMerge(savedSession.getCollectedFacts())
                .missingCriticalFacts(completionEngine.getMissingCriticalFacts(savedSession.getPrimaryCategory(), savedSession.getCollectedFacts(), savedSession.getJurisdictionCity(), savedSession.getJurisdictionState()))
                .aiNextQuestionFactKey(aiResponse != null ? aiResponse.getNextQuestionFactKey() : null)
                .aiNextQuestion(aiResponse != null ? aiResponse.getNextQuestion() : null)
                .antiLoopRejected(antiLoopRejectedHolder[0])
                .regenerationAttempted(regenerationAttemptedHolder[0])
                .deterministicFallbackUsed(fallbackUsedHolder[0])
                .finalPersistedQuestion(finalPersistedQHolder[0])
                .finalApiAssistantMessage(finalAssistantMsg)
                .build();
        if (tracer != null) tracer.logTrace(trace);

        return buildFrontendResponse(savedSession, finalAssistantMsg);
    }

    private AiIntakeResponseDTO handleStandardIntakeWorkflow(LegalIntakeSession session,
                                              AiIntakeResponseDTO aiResponse,
                                              AiIntakeRequestDTO aiRequest,
                                              String userText,
                                              String detectedLang,
                                              boolean[] antiLoopRejectedHolder,
                                              boolean[] regenerationAttemptedHolder,
                                              boolean[] fallbackUsedHolder,
                                              String[] finalPersistedQHolder) {

        String proposedKey = aiResponse != null ? aiResponse.getNextQuestionFactKey() : null;
        String proposedQuestion = aiResponse != null ? aiResponse.getNextQuestion() : null;

        // Check for duplicate question using AntiLoopEngine
        boolean isDuplicate = antiLoopEngine.isDuplicateQuestion(
                proposedKey,
                proposedQuestion,
                session.getAskedFactKeys(),
                session.getCollectedFacts(),
                session.getAskedQuestions()
        );

        if (!isDuplicate && proposedQuestion != null && !proposedQuestion.isBlank()) {
            String normP = antiLoopEngine.normalizeText(proposedQuestion);
            if (session.getAskedQuestions().stream().anyMatch(q -> antiLoopEngine.normalizeText(q).equals(normP))) {
                log.warn("Anti-loop: proposed question text matches an already-asked question normalized string.");
                isDuplicate = true;
            }
        }

        if (isDuplicate) {
            antiLoopRejectedHolder[0] = true;
            log.warn("Anti-loop triggered for session {} on proposed key '{}'", session.getId(), proposedKey);

            // First check if intake is already sufficient
            boolean alreadySufficient = completionEngine.isIntakeComplete(
                    session.getPrimaryCategory(),
                    session.getCollectedFacts(),
                    session.getJurisdictionCity(),
                    session.getJurisdictionState(),
                    session.getQuestionCount(),
                    false
            );

            if (alreadySufficient) {
                log.info("Intake is already sufficient. Finalizing summary instead of looping.");
                finalizeIntake(session, aiResponse, userText);
                return aiResponse;
            }

            // Not sufficient: Attempt at most ONE retry to AI with explicit guidance
            List<String> missing = completionEngine.getMissingCriticalFacts(
                    session.getPrimaryCategory(),
                    session.getCollectedFacts(),
                    session.getJurisdictionCity(),
                    session.getJurisdictionState()
            );

            regenerationAttemptedHolder[0] = true;
            AiIntakeResponseDTO retryResponse = attemptAiRetry(aiRequest, missing);
            boolean retryIsDuplicate = (retryResponse == null) || antiLoopEngine.isDuplicateQuestion(
                    retryResponse.getNextQuestionFactKey(),
                    retryResponse.getNextQuestion(),
                    session.getAskedFactKeys(),
                    session.getCollectedFacts(),
                    session.getAskedQuestions()
            );
            if (!retryIsDuplicate && retryResponse != null && retryResponse.getNextQuestion() != null) {
                String normRetry = antiLoopEngine.normalizeText(retryResponse.getNextQuestion());
                if (session.getAskedQuestions().stream().anyMatch(q -> antiLoopEngine.normalizeText(q).equals(normRetry))) {
                    retryIsDuplicate = true;
                }
            }

            if (!retryIsDuplicate) {
                // Retry succeeded with a clean new question
                aiResponse = retryResponse;
                proposedKey = retryResponse.getNextQuestionFactKey();
                proposedQuestion = retryResponse.getNextQuestion();
            } else {
                // Retry also failed or gave duplicate: find a truly UNASKED fallback key & question in detected language
                fallbackUsedHolder[0] = true;
                List<String> remainingMissing = completionEngine.getMissingCriticalFacts(
                        session.getPrimaryCategory(),
                        session.getCollectedFacts(),
                        session.getJurisdictionCity(),
                        session.getJurisdictionState()
                );

                String chosenFallbackKey = null;
                String chosenFallbackQ = null;

                if (remainingMissing != null) {
                    for (String key : remainingMissing) {
                        if (session.getAskedFactKeys().contains(key.trim().toLowerCase())) {
                            continue;
                        }
                        String fbQ = antiLoopEngine.getFallbackQuestion(session.getPrimaryCategory(), key, detectedLang, session.getCollectedFacts());
                        if (fbQ == null) continue;
                        String normFb = antiLoopEngine.normalizeText(fbQ);
                        if (session.getAskedQuestions().stream().noneMatch(q -> antiLoopEngine.normalizeText(q).equals(normFb))) {
                            chosenFallbackKey = key;
                            chosenFallbackQ = fbQ;
                            break;
                        }
                    }
                }

                if (chosenFallbackKey == null) {
                    // No unasked fallback question remains -> finalize intake!
                    log.info("No unasked fallback questions remain for missing dimensions. Finalizing intake.");
                    finalizeIntake(session, aiResponse, userText);
                    return aiResponse;
                }

                String fallbackKey = chosenFallbackKey;
                String fallbackQ = chosenFallbackQ;

                if (isGuidanceRequested(userText)) {
                    boolean hasPolice = antiLoopEngine.hasPoliceInCollectedFacts(session.getCollectedFacts());
                    boolean hasEvidence = antiLoopEngine.hasEvidenceInCollectedFacts(session.getCollectedFacts());

                    StringBuilder guidanceMsg = new StringBuilder();
                    if ("ENGLISH".equals(detectedLang)) {
                        if (hasPolice || hasEvidence) {
                            guidanceMsg.append("Understood. ");
                            if (hasPolice) guidanceMsg.append("Keep your FIR copy or police complaint acknowledgment safe. Since the police complaint was filed prior to the accident, direct legal liability will not fall on you. ");
                            if (hasEvidence) guidanceMsg.append("Keep all your evidence (WhatsApp chats/screenshots) safe and do not delete original messages. ");
                        } else {
                            guidanceMsg.append("Please keep all documents and evidence safe. ");
                        }
                    } else if ("HINDI".equals(detectedLang)) {
                        if (hasPolice || hasEvidence) {
                            guidanceMsg.append("ठीक है। ");
                            if (hasPolice) guidanceMsg.append("अपनी एफआईआर की प्रति या पुलिस शिकायत की पावती सुरक्षित रखें। चूंकि पुलिस शिकायत हादसे से पहले की है, इसलिए सीधी जिम्मेदारी आप पर नहीं आएगी। ");
                            if (hasEvidence) guidanceMsg.append("अपने पास मौजूद सभी सबूत (व्हाट्सएप चैट/स्क्रीनशॉट) सुरक्षित रखें और मूल संदेशों को हटाएँ नहीं। ");
                        } else {
                            guidanceMsg.append("कृपया सभी दस्तावेज और सबूत सुरक्षित रखें। ");
                        }
                    } else {
                        if (hasPolice || hasEvidence) {
                            guidanceMsg.append("ठीक आहे. ");
                            if (hasPolice) guidanceMsg.append("तुमची FIR ची प्रत किंवा पोलीस तक्रारीची acknowledgment सुरक्षित ठेवा. गाडी चोरीला गेल्याची नोंद पोलिसांत असल्याने नंतर झालेल्या अपघाताची थेट कायदेशीर जबाबदारी तुमच्यावर येणार नाही. ");
                            if (hasEvidence) guidanceMsg.append("तुमच्याकडे असलेला पुरावा सुरक्षित ठेवा व ओरिजिनल मेसेज/कागदपत्रे जतन करा. ");
                        } else {
                            guidanceMsg.append("या प्रकरणाबाबत सर्व पुरावे आणि माहिती सुरक्षित ठेवा. ");
                        }
                    }

                    guidanceMsg.append("\n\n").append(fallbackQ);

                    proposedKey = fallbackKey;
                    proposedQuestion = fallbackQ;
                    if (aiResponse != null) {
                        aiResponse.setNextQuestionFactKey(fallbackKey);
                        aiResponse.setNextQuestion(fallbackQ);
                        aiResponse.setAssistantMessage(guidanceMsg.toString());
                    }
                } else {
                    proposedKey = fallbackKey;
                    proposedQuestion = fallbackQ;
                    if (aiResponse != null) {
                        aiResponse.setNextQuestionFactKey(fallbackKey);
                        aiResponse.setNextQuestion(fallbackQ);
                        aiResponse.setAssistantMessage(fallbackQ);
                    }
                }
            }
        }

        // Check CompletionEngine (Spring Boot is authoritative)
        boolean isComplete = completionEngine.isIntakeComplete(
                session.getPrimaryCategory(),
                session.getCollectedFacts(),
                session.getJurisdictionCity(),
                session.getJurisdictionState(),
                session.getQuestionCount() + 1,
                aiResponse != null && aiResponse.isIntakeComplete()
        );

        if (isComplete) {
            finalizeIntake(session, aiResponse, userText);
        } else {
            String displayMsg = (aiResponse != null && aiResponse.getAssistantMessage() != null && !aiResponse.getAssistantMessage().isBlank())
                    ? aiResponse.getAssistantMessage()
                    : proposedQuestion;

            // Final Guard: Check if displayMsg (or proposedQuestion) is duplicate of past asked questions
            if (displayMsg != null && !displayMsg.isBlank()) {
                String normDisplay = antiLoopEngine.normalizeText(displayMsg);
                boolean textAlreadyAsked = session.getAskedQuestions().stream()
                        .anyMatch(q -> antiLoopEngine.normalizeText(q).equals(normDisplay));

                if (textAlreadyAsked) {
                    log.warn("Final Guard: displayMsg text '{}' was already asked previously. Finalizing intake.", displayMsg);
                    finalizeIntake(session, aiResponse, userText);
                    return aiResponse;
                }

                session.setQuestionCount(session.getQuestionCount() + 1);
                if (proposedKey != null) {
                    session.getAskedFactKeys().add(proposedKey.trim().toLowerCase());
                    session.setLastAskedFactKey(proposedKey.trim().toLowerCase());
                }
                if (proposedQuestion != null) {
                    session.getAskedQuestions().add(proposedQuestion.trim());
                    finalPersistedQHolder[0] = proposedQuestion.trim();
                }

                persistAiMessage(session, displayMsg, MessageType.QUESTION);
            }
        }
        return aiResponse;
    }

    private AiIntakeResponseDTO handleSummaryEditWorkflow(LegalIntakeSession session,
                                            AiIntakeResponseDTO aiResponse,
                                            String userText) {
        log.info("Processing summary edit for session {}", session.getId());

        // Always regenerate/update summaries based on the newly merged facts
        String updatedCustomerSum = (aiResponse.getCustomerSummary() != null && !aiResponse.getCustomerSummary().isBlank())
                ? aiResponse.getCustomerSummary()
                : generateCustomerSummary(session);

        String updatedLawyerSum = (aiResponse.getLawyerSummary() != null && !aiResponse.getLawyerSummary().isBlank())
                ? aiResponse.getLawyerSummary()
                : generateLawyerSummary(session);

        session.setCustomerSummary(updatedCustomerSum);
        session.setLawyerSummary(updatedLawyerSum);

        // Ensure session remains in SUMMARY_READY
        session.setStatus(IntakeStatus.SUMMARY_READY);
        session.setIntakeComplete(true);

        String assistantMsg = updatedCustomerSum;
        if (!assistantMsg.contains("correct") && !assistantMsg.contains("बरोबर") && !assistantMsg.contains("सही")) {
            assistantMsg += "\n\nIs this updated information correct, or would you like to change or add anything?";
        }
        persistAiMessage(session, assistantMsg, MessageType.SUMMARY);
        aiResponse.setAssistantMessage(assistantMsg);
        return aiResponse;
    }

    private void finalizeIntake(LegalIntakeSession session, AiIntakeResponseDTO aiResponse, String userText) {
        session.setStatus(IntakeStatus.SUMMARY_READY);
        session.setIntakeComplete(true);

        // Use AI summaries if valid, otherwise build standard structured summaries
        String customerSum = (aiResponse.getCustomerSummary() != null && !aiResponse.getCustomerSummary().isBlank())
                ? aiResponse.getCustomerSummary()
                : generateCustomerSummary(session);

        String lawyerSum = (aiResponse.getLawyerSummary() != null && !aiResponse.getLawyerSummary().isBlank())
                ? aiResponse.getLawyerSummary()
                : generateLawyerSummary(session);

        session.setCustomerSummary(customerSum);
        session.setLawyerSummary(lawyerSum);

        // Prompt user to confirm or edit
        String confirmationEnding = "\n\nIs this information correct, or would you like to change or add anything?";
        String fullAssistantMessage = customerSum;
        if (!fullAssistantMessage.contains("correct") && !fullAssistantMessage.contains("बरोबर") && !fullAssistantMessage.contains("सही")) {
            fullAssistantMessage += confirmationEnding;
        }

        persistAiMessage(session, fullAssistantMessage, MessageType.SUMMARY);
    }

    private AiIntakeResponseDTO attemptAiRetry(AiIntakeRequestDTO originalRequest, List<String> missingCriticalKeys) {
        try {
            String retryGuidance = "CRITICAL INSTRUCTION: The previously suggested question targeted an already-asked or already-known fact. " +
                    "DO NOT repeat previous questions. You MUST target one of these missing critical fact keys instead: " +
                    missingCriticalKeys;

            AiIntakeRequestDTO retryRequest = AiIntakeRequestDTO.builder()
                    .systemRules(originalRequest.getSystemRules() + "\n\n" + retryGuidance)
                    .currentState(originalRequest.getCurrentState())
                    .askedFactKeys(originalRequest.getAskedFactKeys())
                    .askedQuestions(originalRequest.getAskedQuestions())
                    .missingCriticalFacts(missingCriticalKeys)
                    .latestCustomerMessage(originalRequest.getLatestCustomerMessage())
                    .build();

            return aiProvider.processMessage(retryRequest);
        } catch (Exception e) {
            log.warn("AI retry failed: {}", e.getMessage());
            return null;
        }
    }

    private void updateSessionMetadata(LegalIntakeSession session, AiIntakeResponseDTO aiResponse) {
        if (aiResponse == null) return;
        // Primary category validation
        if (aiResponse.getPrimaryCategory() != null && !aiResponse.getPrimaryCategory().isBlank()) {
            try {
                LegalCategory category = LegalCategory.valueOf(aiResponse.getPrimaryCategory().toUpperCase().trim());
                if (session.getPrimaryCategory() == null || session.getPrimaryCategory() == LegalCategory.OTHER_LEGAL) {
                    session.setPrimaryCategory(category);
                }
            } catch (IllegalArgumentException e) {
                log.debug("Unknown category '{}', preserving existing or defaulting to OTHER_LEGAL", aiResponse.getPrimaryCategory());
                if (session.getPrimaryCategory() == null) {
                    session.setPrimaryCategory(LegalCategory.OTHER_LEGAL);
                }
            }
        }

        // Jurisdiction validation
        if (aiResponse.getJurisdiction() != null) {
            if (factMergeService.isMeaningfulValue(aiResponse.getJurisdiction().getState())) {
                session.setJurisdictionState(aiResponse.getJurisdiction().getState().trim());
            }
            if (factMergeService.isMeaningfulValue(aiResponse.getJurisdiction().getCity())) {
                session.setJurisdictionCity(aiResponse.getJurisdiction().getCity().trim());
            }
        }

        // Urgency validation
        if (aiResponse.getUrgency() != null) {
            String urgencyUpper = aiResponse.getUrgency().toUpperCase().trim();
            if (ALLOWED_URGENCY.contains(urgencyUpper)) {
                session.setUrgency(urgencyUpper);
            }
        }
    }

    private boolean isOutOfScope(AiIntakeResponseDTO aiResponse) {
        if (aiResponse == null) return false;
        boolean noCategory = aiResponse.getPrimaryCategory() == null || aiResponse.getPrimaryCategory().isBlank();
        boolean noFacts = aiResponse.getNewFacts() == null || aiResponse.getNewFacts().isEmpty();
        boolean noNextQuestion = aiResponse.getNextQuestion() == null || aiResponse.getNextQuestion().isBlank();
        return noCategory && noFacts && noNextQuestion && !aiResponse.isIntakeComplete();
    }

    private void persistAiMessage(LegalIntakeSession session, String text, MessageType type) {
        LegalIntakeMessage aiMsg = LegalIntakeMessage.builder()
                .session(session)
                .senderType(SenderType.AI)
                .messageType(type)
                .message(text)
                .build();
        messageRepository.save(aiMsg);
    }

    private AiIntakeRequestDTO buildAiRequest(LegalIntakeSession session, String userText, boolean isSummaryEdit) {
        List<String> missingCritical = completionEngine.getMissingCriticalFacts(
                session.getPrimaryCategory(),
                session.getCollectedFacts(),
                session.getJurisdictionCity(),
                session.getJurisdictionState()
        );

        AiIntakeRequestDTO.CurrentState state = AiIntakeRequestDTO.CurrentState.builder()
                .primaryCategory(session.getPrimaryCategory() != null ? session.getPrimaryCategory().name() : null)
                .collectedFacts(session.getCollectedFacts())
                .jurisdiction(JurisdictionDTO.builder()
                        .country("India")
                        .state(session.getJurisdictionState())
                        .city(session.getJurisdictionCity())
                        .build())
                .urgency(session.getUrgency())
                .build();

        String rulesAddition = isSummaryEdit
                ? "\n\nNOTE: The customer is reviewing or editing their case summary. Merge any corrections and regenerate the updated customerSummary and lawyerSummary."
                : "";

        rulesAddition += "\n\nCRITICAL INSTRUCTION: If the user provides a short one-word answer like 'yes', 'no', 'nahi', 'ho', 'nai', bind it directly to the previousQuestionFactKey. " +
                         "Also, extract ALL possible facts from the user's message, even if they answer more than one thing at a time.";

        if (isGuidanceRequested(userText)) {
            rulesAddition += "\n\nCRITICAL GUIDANCE INSTRUCTION: The user is explicitly asking 'what should I do now?' / 'mag atta' (guidance intent). " +
                             "You MUST FIRST provide concise, practical, safe legal guidance based on the facts known so far (e.g. keep police complaint FIR copy/acknowledgment safe, preserve WhatsApp screenshots, do not delete original messages). " +
                             "THEN, if critical intake facts are still missing, append at most ONE genuinely missing follow-up question. " +
                             "Do NOT re-ask whether evidence, messages, or police complaints exist if they are already known!";
        }

        String prevQuestionText = session.getAskedQuestions().isEmpty() ? null : session.getAskedQuestions().get(session.getAskedQuestions().size() - 1);

        return AiIntakeRequestDTO.builder()
                .systemRules(rulesAddition)
                .currentState(state)
                .askedFactKeys(new HashSet<>(session.getAskedFactKeys()))
                .askedQuestions(new ArrayList<>(session.getAskedQuestions()))
                .missingCriticalFacts(missingCritical)
                .latestCustomerMessage(userText)
                .previousQuestionFactKey(session.getLastAskedFactKey())
                .previousQuestionText(prevQuestionText)
                .build();
    }

    public boolean isGuidanceRequested(String userText) {
        if (userText == null || userText.isBlank()) return false;
        String t = userText.toLowerCase();
        return t.contains("atta kay karu") ||
               t.contains("mag atta") ||
               t.contains("what should i do") ||
               t.contains("ab kya karu") ||
               t.contains("pudhe kay") ||
               t.contains("next kay") ||
               t.contains("kay kel pahij") ||
               t.contains("kay karaych") ||
               t.contains("guide kar") ||
               t.contains("kay karu te") ||
               t.contains("atta kay") ||
               t.contains("atta kay karaycha") ||
               t.contains("case mazya vr honar ka") ||
               t.contains("mala problem hoil ka") ||
               t.contains("will i be liable") ||
               t.contains("mazya vr case") ||
               t.contains("mazya var case");
    }

    public String detectLanguage(String text, Map<String, Object> collectedFacts) {
        if (text == null || text.isBlank()) {
            if (collectedFacts != null && collectedFacts.containsKey("preferred_language")) {
                return collectedFacts.get("preferred_language").toString();
            }
            return "MARATHI";
        }

        String input = text.trim();

        // 1. Check for Devanagari script (\p{IsDevanagari})
        boolean hasDevanagari = false;
        for (char c : input.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI) {
                hasDevanagari = true;
                break;
            }
        }

        if (hasDevanagari) {
            String lower = input.toLowerCase();
            // Distinctive Hindi words in Devanagari
            if (lower.contains("है") || lower.contains("नहीं") || lower.contains("क्या") || lower.contains("हुआ") || lower.contains("करें") || lower.contains("पुलिस") || lower.contains("मुझे")) {
                return "HINDI";
            }
            // Distinctive Marathi words in Devanagari
            if (lower.contains("आहे") || lower.contains("नाही") || lower.contains("काय") || lower.contains("झाले") || lower.contains("करावे") || lower.contains("पोलीस") || lower.contains("मला") || lower.contains("माझ्या")) {
                return "MARATHI";
            }
            return "MARATHI";
        }

        // 2. Script is Latin
        String lower = input.toLowerCase();
        int englishCount = 0;
        int marathiCount = 0;
        int hindiCount = 0;

        String[] words = lower.split("[^a-zA-Z0-9]+");
        Set<String> marathiMarkers = Set.of("ahe", "nahi", "kay", "karu", "mazi", "mazya", "mala", "tula", "tyala", "tila", "sagla", "ho", "keli", "geli", "kela", "hot", "koni", "ani", "tar", "mhanun", "sang", "sanga", "bata");
        Set<String> hindiMarkers = Set.of("mera", "meri", "mere", "mujhe", "hai", "hain", "nahin", "kya", "karna", "hua", "gaya", "gayi", "batao", "bataen", "chahiye", "huva");
        Set<String> englishMarkers = Set.of("i", "my", "me", "we", "you", "he", "she", "they", "need", "lawyer", "advocate", "help", "car", "stolen", "accident", "police", "fir", "theft", "landlord", "salary", "rent", "work", "job", "company", "evidence", "proof");

        for (String w : words) {
            if (marathiMarkers.contains(w)) marathiCount++;
            if (hindiMarkers.contains(w)) hindiCount++;
            if (englishMarkers.contains(w)) englishCount++;
        }

        if (marathiCount > hindiCount && marathiCount > 0) return "MARATHI";
        if (hindiCount > marathiCount && hindiCount > 0) return "HINDI";
        if (englishCount > (marathiCount + hindiCount)) return "ENGLISH";

        if (collectedFacts != null && collectedFacts.containsKey("preferred_language")) {
            return collectedFacts.get("preferred_language").toString();
        }

        return "ENGLISH";
    }

    public String detectIntent(String userText, IntakeStatus status, boolean isLawyerReq, boolean isGuidanceReq, boolean isSummaryEdit) {
        if (userText == null || userText.isBlank()) return "CLARIFICATION_NEEDED";

        if (isUnclearOrGarbled(userText, status, null)) {
            return "CLARIFICATION_NEEDED";
        }

        String lower = userText.trim().toLowerCase();

        // Priority 1: Check for META_CONTEXT_REQUEST
        if (isMetaContextRequest(lower)) {
            return "META_CONTEXT_REQUEST";
        }

        // Priority 2: When in SUMMARY_READY status, evaluate confirmation vs correction first
        if (status == IntakeStatus.SUMMARY_READY) {
            if (isSummaryConfirmation(lower)) {
                return "SUMMARY_CONFIRMATION";
            } else {
                return "SUMMARY_CORRECTION";
            }
        }

        if (isSummaryEdit) return "SUMMARY_EDIT";

        Set<String> greetings = Set.of("hi", "hello", "namaste", "namaskar", "hey", "good morning", "good evening", "hi there");
        if (greetings.contains(lower) || lower.equals("hi") || lower.equals("hello")) {
            return "GREETING";
        }

        if (isLawyerReq) return "LAWYER_REQUEST";
        if (isGuidanceReq) return "GUIDANCE_REQUEST";

        return "INTAKE_FACTS";
    }

    public boolean isUnclearOrGarbled(String text, IntakeStatus status, String lastAskedFactKey) {
        if (text == null || text.isBlank()) return true;
        String t = text.trim();

        // 1. Punctuation/symbols only (no letters or digits in any script)
        if (t.matches("[^\\p{L}0-9]+")) {
            return true;
        }

        // 2. Pure numbers when not in response to a numeric question
        if (t.matches("\\d+")) {
            boolean expectsNumber = lastAskedFactKey != null && (
                    lastAskedFactKey.contains("month") || lastAskedFactKey.contains("amount") ||
                    lastAskedFactKey.contains("salary") || lastAskedFactKey.contains("period") ||
                    lastAskedFactKey.contains("year") || lastAskedFactKey.contains("fee")
            );
            if (!expectsNumber && t.length() > 3) {
                return true;
            }
        }

        // 3. Keyboard mash: 5+ consecutive consonants in Latin script (e.g. "asdfghjkl", "qwertyuiop", "zxcvbnm")
        String lower = t.toLowerCase();
        if (lower.matches(".*[bcdfghjklmnpqrstvwxz]{5,}.*")) {
            return true;
        }

        // 4. Repeated single/double character sequence like "zzzzzzzz" or "aaaaaaa"
        if (t.length() >= 5 && t.chars().distinct().count() <= 2) {
            return true;
        }

        return false;
    }

    private String getClarificationMessage(String detectedLang) {
        if ("ENGLISH".equals(detectedLang)) {
            return "I'm sorry, I couldn't clearly understand what you meant. Could you please explain it again in a little more detail?";
        } else if ("HINDI".equals(detectedLang)) {
            return "माफ कीजिए, मैं आपकी बात ठीक से समझ नहीं पाया। कृपया इसे थोड़ा स्पष्ट करके दोबारा बताइए।";
        } else {
            return "माफ करा, तुम्ही काय म्हणत आहात ते मला नीट समजलं नाही. कृपया पुन्हा थोडं स्पष्ट सांगाल का?";
        }
    }

    private boolean isMetaContextRequest(String lower) {
        return lower.contains("topic kay suru") ||
               lower.contains("apla topic") ||
               lower.contains("topic kay ahe") ||
               lower.contains("what were we talking") ||
               lower.contains("what are we talking") ||
               lower.contains("what topic") ||
               lower.contains("what case are we") ||
               lower.contains("kuthlya topic") ||
               lower.contains("kuthli case") ||
               lower.contains("kashabaddal boltoy") ||
               lower.contains("kashabaddal boltoya") ||
               lower.contains("hum kis baare mein") ||
               lower.contains("kis case ke baare") ||
               lower.contains("kaun sa case");
    }

    private boolean isSummaryConfirmation(String lower) {
        Set<String> confirmationWords = Set.of(
                "yes", "yess", "yeah", "yup", "ho", "हो", "barobar", "बरोबर", "sahi", "sahi hai",
                "correct", "right", "haa", "haan", "हां", "thik ahe", "thik hai", "ok", "okay", "fine",
                "confirm", "confirmed", "no changes", "all good", "everything is correct", "changa"
        );

        if (confirmationWords.contains(lower)) {
            return true;
        }

        boolean startsWithConf = false;
        for (String word : confirmationWords) {
            if (lower.startsWith(word + " ") || lower.startsWith(word + ",") || lower.startsWith(word + ".")) {
                startsWithConf = true;
                break;
            }
        }

        if (startsWithConf) {
            boolean containsCorrection = lower.contains("but") || lower.contains("pan") || lower.contains("par") ||
                    lower.contains("lekin") || lower.contains("change") || lower.contains("badla") ||
                    lower.contains("chukiche") || lower.contains("instead of") || lower.contains("correct it to");
            return !containsCorrection;
        }

        return false;
    }

    private String buildMetaContextResponse(LegalIntakeSession session, String detectedLang) {
        String topic = "कायदेशीर";
        Map<String, Object> facts = session.getCollectedFacts();
        if (facts != null) {
            if (facts.containsKey("core_problem")) topic = facts.get("core_problem").toString();
            else if (facts.containsKey("incident")) topic = facts.get("incident").toString();
            else if (facts.containsKey("offence_type")) topic = facts.get("offence_type").toString();
        }
        if (session.getPrimaryCategory() != null && "कायदेशीर".equals(topic)) {
            topic = session.getPrimaryCategory().getDisplayName();
        }

        if ("ENGLISH".equals(detectedLang)) {
            return "We are discussing your legal matter: " + topic + ".";
        } else if ("HINDI".equals(detectedLang)) {
            return "हम आपके कानूनी मामले (" + topic + ") के विषय में बात कर रहे हैं।";
        } else {
            return "आपण तुमच्या " + topic + " या विषयावर बोलत आहोत.";
        }
    }

    private String getAiFailureFallbackMessage(String detectedLang) {
        if ("MARATHI".equalsIgnoreCase(detectedLang)) {
            return "एआय असिस्टंट सध्या तात्पुरता उपलब्ध नाही. तुमचा संदेश जतन करण्यात आला आहे. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.";
        } else if ("HINDI".equalsIgnoreCase(detectedLang)) {
            return "एआई असिस्टेंट अभी अस्थायी रूप से उपलब्ध नहीं है। आपका संदेश सहेज लिया गया है। कृपया थोड़ी देर बाद पुनः प्रयास करें।";
        }
        return "The Legal Assistant is temporarily unavailable. Your message has been saved. Please try again shortly.";
    }

    private LegalIntakeResponseDTO buildFrontendResponse(LegalIntakeSession session, String assistantMessage) {
        boolean isSummaryReady = session.getStatus() == IntakeStatus.SUMMARY_READY;
        boolean isAwaitingNextStep = session.getStatus() == IntakeStatus.AWAITING_NEXT_STEP;
        boolean isConfirmed = session.isConfirmed() || isAwaitingNextStep || session.getStatus() == IntakeStatus.CONFIRMED || session.getStatus() == IntakeStatus.ASSIGNED || session.getStatus() == IntakeStatus.AI_ONLY_COMPLETED;
        
        List<LegalIntakeMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<LegalIntakeMessageDTO> messageHistory = messages.stream().map(m -> 
            LegalIntakeMessageDTO.builder()
                .id(m.getId())
                .senderType(m.getSenderType() != null ? m.getSenderType().name() : null)
                .message(m.getMessage())
                .createdAt(m.getCreatedAt())
                .build()
        ).toList();

        // Build LawyerInfoDTO if consultation assigned
        LawyerInfoDTO lawyerInfo = null;
        if (session.getAssignedConsultationRequestId() != null) {
            Optional<ConsultationRequest> crOpt = consultationRequestRepository.findById(session.getAssignedConsultationRequestId());
            if (crOpt.isPresent() && crOpt.get().getLawyer() != null) {
                Lawyer l = crOpt.get().getLawyer();
                lawyerInfo = LawyerInfoDTO.builder()
                        .lawyerId(l.getLawyerId())
                        .fullName(l.getFullName())
                        .location(l.getLocation())
                        .practiceAreas(l.getPracticeAreas())
                        .rating(l.getRating())
                        .consultationFee(l.getConsultationFee())
                        .build();
            }
        }

        List<String> actions = isAwaitingNextStep ? List.of("CONNECT_LAWYER", "AI_ONLY") : List.of();

        List<LawyerInfoDTO> suggestedLawyers = null;
        if (session.getQuestionCount() >= 8 || session.isIntakeComplete()) {
            List<Lawyer> topLawyers = lawyerMatchingService.findTopMatches(
                    session.getPrimaryCategory(), session.getJurisdictionCity(), 5);
            if (!topLawyers.isEmpty()) {
                suggestedLawyers = topLawyers.stream().map(l -> LawyerInfoDTO.builder()
                        .lawyerId(l.getLawyerId())
                        .fullName(l.getFullName())
                        .location(l.getLocation())
                        .practiceAreas(l.getPracticeAreas())
                        .rating(l.getRating())
                        .consultationFee(l.getConsultationFee())
                        .build()
                ).toList();
            }
        }

        return LegalIntakeResponseDTO.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .assistantMessage(assistantMessage)
                .primaryCategory(session.getPrimaryCategory() != null ? session.getPrimaryCategory().name() : null)
                .jurisdiction(JurisdictionDTO.builder()
                        .state(session.getJurisdictionState())
                        .city(session.getJurisdictionCity())
                        .country("India")
                        .build())
                .urgency(session.getUrgency())
                .intakeComplete(session.isIntakeComplete())
                .customerSummary(session.getCustomerSummary())
                .canConfirm(isSummaryReady)
                .canEdit(isSummaryReady)
                .questionCount(session.getQuestionCount())
                .messageHistory(messageHistory)
                .summaryConfirmed(isConfirmed)
                .nextActionRequired(isAwaitingNextStep)
                .availableActions(actions)
                .consultationId(session.getAssignedConsultationRequestId())
                .matchedLawyer(lawyerInfo)
                .suggestedLawyers(suggestedLawyers)
                .build();
    }

    @Override
    @Transactional
    public LegalIntakeResponseDTO confirmSummary(Long customerId, Long sessionId) {
        LegalIntakeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal intake session not found with ID: " + sessionId));

        if (!session.getCustomer().getCustomerId().equals(customerId)) {
            throw new SecurityException("Unauthorized access to legal intake session.");
        }

        session.setConfirmed(true);
        session.setStatus(IntakeStatus.AWAITING_NEXT_STEP);

        String lang = session.getCollectedFacts() != null && session.getCollectedFacts().containsKey("preferred_language")
                ? session.getCollectedFacts().get("preferred_language").toString()
                : "MARATHI";

        String nextStepMessage;
        if ("ENGLISH".equals(lang)) {
            nextStepMessage = "Your case details have been confirmed.\n\n" +
                    "Would you like to connect with a verified advocate for your case (Connect with Advocate), " +
                    "or use AI Legal Assistant guidance only (AI Guidance Only)?";
        } else if ("HINDI".equals(lang)) {
            nextStepMessage = "आपकी जानकारी की पुष्टि हो गई है।\n\n" +
                    "क्या आप अपने केस के लिए वकील से जुड़ना चाहते हैं (Connect with Advocate), " +
                    "या केवल AI मार्गदर्शन प्राप्त करना चाहते हैं (AI Guidance Only)?";
        } else {
            nextStepMessage = "माहिती confirm झाली आहे.\n\n" +
                    "पुढे तुम्हाला या प्रकरणासाठी वकील जोडायचा आहे का (Connect with Advocate), " +
                    "की सध्या फक्त AI Legal Assistant कडून मार्गदर्शन हवे आहे (AI Guidance Only)?";
        }

        persistAiMessage(session, nextStepMessage, MessageType.TEXT);
        LegalIntakeSession saved = sessionRepository.save(session);

        return buildFrontendResponse(saved, nextStepMessage);
    }

    @Override
    @Transactional
    public LegalIntakeResponseDTO handleNextStepChoice(Long customerId, Long sessionId, String action) {
        LegalIntakeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal intake session not found with ID: " + sessionId));

        if (!session.getCustomer().getCustomerId().equals(customerId)) {
            throw new SecurityException("Unauthorized access to legal intake session.");
        }

        if ("CONNECT_LAWYER".equalsIgnoreCase(action)) {
            // Idempotency check: reuse existing consultation request if already created
            if (session.getAssignedConsultationRequestId() != null) {
                log.info("Idempotent lawyer handoff for session {}: returning existing consultation ID {}", sessionId, session.getAssignedConsultationRequestId());
                session.setStatus(IntakeStatus.ASSIGNED);
                sessionRepository.save(session);
                return buildFrontendResponse(session, "तुमचा वकिलांशी जोडणीचा अर्ज आधीच नोंदवला आहे. थोड्यात वेळात वकील तुमच्याशी संपर्क साधतील.");
            }

            // Find best matching lawyer
            Optional<Lawyer> matchedLawyerOpt = lawyerMatchingService.findBestMatch(
                    session.getPrimaryCategory(),
                    session.getJurisdictionCity(),
                    session.getJurisdictionState()
            );

            if (matchedLawyerOpt.isPresent()) {
                Lawyer lawyer = matchedLawyerOpt.get();

                ConsultationRequest consultationRequest = ConsultationRequest.builder()
                        .customer(session.getCustomer())
                        .lawyer(lawyer)
                        .category(session.getPrimaryCategory())
                        .practiceArea(session.getPrimaryCategory() != null ? session.getPrimaryCategory().getDefaultPracticeArea() : null)
                        .caseSummary(session.getLawyerSummary() != null ? session.getLawyerSummary() : session.getCustomerSummary())
                        .status(ConsultationRequestStatus.REQUESTED)
                        .build();

                ConsultationRequest savedCR = consultationRequestRepository.save(consultationRequest);

                session.setAssignedConsultationRequestId(savedCR.getId());
                session.setStatus(IntakeStatus.ASSIGNED);
                sessionRepository.save(session);

                String msg = "तुमचे प्रकरण यशस्वीरित्या ॲडव्होकेट " + lawyer.getFullName() + " यांच्याकडे वर्ग करण्यात आले आहे. ते लवकरच तुमच्याशी संपर्क साधतील.";
                persistAiMessage(session, msg, MessageType.TEXT);

                return buildFrontendResponse(session, msg);
            } else {
                // No eligible lawyer available
                session.setStatus(IntakeStatus.NO_LAWYER_AVAILABLE);
                sessionRepository.save(session);

                String msg = "तुमच्या प्रकरणाची माहिती सुरक्षितपणे नोंदवली आहे. सध्या योग्य उपलब्ध वकील मिळालेला नाही. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.";
                persistAiMessage(session, msg, MessageType.TEXT);

                return buildFrontendResponse(session, msg);
            }
        } else if ("AI_ONLY".equalsIgnoreCase(action)) {
            session.setStatus(IntakeStatus.AI_ONLY_COMPLETED);
            sessionRepository.save(session);

            String msg = "धन्यवाद! तुमच्या प्रकरणाची सर्व माहिती सुरक्षितपणे सेव्ह केली आहे. तुम्हाला AI Legal Assistant कडून अतिरिक्त कायदेशीर माहिती हवी असल्यास तुम्ही इथे कधीही विचारू शकता. जर तुम्हाला नंतर वकीलाचा सल्ला हवा असेल, तर 'मला वकील हवा आहे' असे सांगू शकता.";
            persistAiMessage(session, msg, MessageType.TEXT);

            return buildFrontendResponse(session, msg);
        } else {
            throw new IllegalArgumentException("Invalid action: " + action + ". Must be CONNECT_LAWYER or AI_ONLY.");
        }
    }

    private String generateCustomerSummary(LegalIntakeSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Case Category: ").append(session.getPrimaryCategory() != null ? session.getPrimaryCategory().getDisplayName() : "General Legal Matter").append("\n");
        if (session.getJurisdictionCity() != null) {
            sb.append("Location: ").append(session.getJurisdictionCity());
            if (session.getJurisdictionState() != null) sb.append(", ").append(session.getJurisdictionState());
            sb.append("\n");
        }
        sb.append("\nBased on your description, here are the key facts we gathered:\n");
        session.getCollectedFacts().forEach((k, v) -> {
            sb.append("• ").append(k.replace("_", " ")).append(": ").append(v).append("\n");
        });
        return sb.toString();
    }

    private String generateLawyerSummary(LegalIntakeSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("  ADALAT AI — LAWYER INTAKE BRIEF\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        sb.append("📁 CASE CATEGORY: ").append(session.getPrimaryCategory() != null ? session.getPrimaryCategory().getDisplayName() : "UNKNOWN").append("\n");
        sb.append("📍 JURISDICTION: ").append(session.getJurisdictionCity() != null ? session.getJurisdictionCity() : "UNKNOWN")
                .append(", ").append(session.getJurisdictionState() != null ? session.getJurisdictionState() : "UNKNOWN").append("\n");
        sb.append("⚠️ URGENCY LEVEL: ").append(session.getUrgency() != null ? session.getUrgency() : "UNKNOWN").append("\n");
        sb.append("📊 QUESTIONS ASKED: ").append(session.getQuestionCount()).append("\n\n");
        
        sb.append("─────────────────────────────────\n");
        sb.append("  COLLECTED CASE FACTS\n");
        sb.append("─────────────────────────────────\n");
        session.getCollectedFacts().forEach((k, v) -> {
            sb.append("• ").append(k.replace("_", " ")).append(": ").append(v).append("\n");
        });

        List<String> missing = completionEngine.getMissingCriticalFacts(
                session.getPrimaryCategory(),
                session.getCollectedFacts(),
                session.getJurisdictionCity(),
                session.getJurisdictionState()
        );
        if (!missing.isEmpty()) {
            sb.append("\n─────────────────────────────────\n");
            sb.append("  UNRESOLVED / MISSING FACTS\n");
            sb.append("─────────────────────────────────\n");
            for (String m : missing) {
                sb.append("• ").append(m.replace("_", " ")).append(": NOT PROVIDED\n");
            }
        }
        
        sb.append("\n─────────────────────────────────\n");
        sb.append("  CLIENT-FACING SUMMARY\n");
        sb.append("─────────────────────────────────\n");
        sb.append(session.getCustomerSummary() != null ? session.getCustomerSummary() : "No summary available.");
        sb.append("\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("  Generated by Adalat AI Legal Assistant\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public LegalIntakeResponseDTO getSessionState(Long customerId, Long sessionId) {
        LegalIntakeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intake session not found with ID: " + sessionId));

        if (!session.getCustomer().getCustomerId().equals(customerId)) {
            throw new SecurityException("Unauthorized access to legal intake session.");
        }

        String latestMessage = session.getCustomerSummary();
        if (latestMessage == null) {
            List<LegalIntakeMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).getSenderType() == SenderType.AI) {
                    latestMessage = messages.get(i).getMessage();
                    break;
                }
            }
        }

        return buildFrontendResponse(session, latestMessage);
    }

    @Override
    @Transactional
    public LegalIntakeResponseDTO getOrCreateActiveSession(Long customerId, boolean forceNew) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        // Look for existing active, summary_ready, or awaiting_next_step session
        Optional<LegalIntakeSession> existing = sessionRepository
                .findFirstByCustomerCustomerIdAndStatusInOrderByIdDesc(customerId, List.of(IntakeStatus.ACTIVE, IntakeStatus.SUMMARY_READY, IntakeStatus.AWAITING_NEXT_STEP));

        if (existing.isPresent()) {
            LegalIntakeSession s = existing.get();
            if (forceNew) {
                // Abandon existing session
                s.setStatus(IntakeStatus.CLOSED);
                sessionRepository.save(s);
            } else {
                return buildFrontendResponse(s, s.getCustomerSummary() != null ? s.getCustomerSummary() : "Welcome back to Adalat Legal Assistant. How can we help you today?");
            }
        }

        // Create new active session
        LegalIntakeSession newSession = LegalIntakeSession.builder()
                .customer(customer)
                .status(IntakeStatus.ACTIVE)
                .questionCount(0)
                .intakeComplete(false)
                .urgency("UNKNOWN")
                .build();

        LegalIntakeSession saved = sessionRepository.save(newSession);

        String initialGreeting = "Hello! I am your Adalat AI Legal Assistant. Please describe your legal problem or what happened, and I will help organize your case for an advocate.";
        persistAiMessage(saved, initialGreeting, MessageType.QUESTION);

        return buildFrontendResponse(saved, initialGreeting);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.adalat.dto.ai.SessionSummaryDTO> getAllSessions(Long customerId) {
        List<LegalIntakeSession> sessions = sessionRepository.findByCustomerCustomerIdOrderByIdDesc(customerId);

        return sessions.stream().map(s -> com.adalat.dto.ai.SessionSummaryDTO.builder()
                .sessionId(s.getId())
                .status(s.getStatus())
                .primaryCategory(s.getPrimaryCategory() != null ? s.getPrimaryCategory().name() : null)
                .categoryDisplay(s.getPrimaryCategory() != null ? s.getPrimaryCategory().getDisplayName() : "General Legal Inquiry")
                .questionCount(s.getQuestionCount())
                .customerSummarySnippet(s.getCustomerSummary() != null
                        ? s.getCustomerSummary().substring(0, Math.min(s.getCustomerSummary().length(), 100))
                        : null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .intakeComplete(s.isIntakeComplete())
                .consultationId(s.getAssignedConsultationRequestId())
                .build()
        ).collect(java.util.stream.Collectors.toList());
    }
}


