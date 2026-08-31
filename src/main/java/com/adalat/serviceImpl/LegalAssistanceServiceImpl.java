package com.adalat.serviceImpl;

import com.adalat.dto.*;
import com.adalat.entity.*;
import com.adalat.enums.*;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.*;
import com.adalat.service.AiLegalAssistantService;
import com.adalat.service.FileStorageService;
import com.adalat.service.LawyerMatchingService;
import com.adalat.service.LegalAssistanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegalAssistanceServiceImpl implements LegalAssistanceService {

    private final LegalAssistanceSessionRepository sessionRepository;
    private final LegalQuestionRepository questionRepository;
    private final LegalAnswerRepository answerRepository;
    private final LegalChatMessageRepository chatMessageRepository;
    private final LegalAssistanceDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final AiLegalAssistantService aiService;
    private final LawyerMatchingService lawyerMatchingService;
    private final FileStorageService fileStorageService;

    // ─── 1. START SESSION ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public StartLegalSessionResponseDTO startSession(Long customerId, StartLegalSessionRequestDTO request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        LegalCategory category = request != null ? request.getSelectedCategory() : null;
        String problemText = request != null ? request.getInitialProblemText() : null;

        LegalAssistanceSession session = LegalAssistanceSession.builder()
                .customer(customer)
                .selectedCategory(category)
                .initialProblemDescription(problemText)
                .status(LegalSessionStatus.STARTED)
                .currentQuestionNumber(0)
                .totalQuestions(8)
                .build();

        if (category != null) {
            session.setPracticeArea(aiService.mapCategoryToPracticeArea(category));
            session.setStatus(LegalSessionStatus.QUESTIONING);
        } else if (problemText != null && !problemText.isBlank()) {
            LegalCategoryResultResponseDTO detection = aiService.detectCategoryFromText(problemText, null);
            session.setAiDetectedCategory(detection.getLikelyLegalCategory());
            session.setPracticeArea(detection.getRelevantPracticeArea());
            session.setStatus(LegalSessionStatus.QUESTIONING);
        }

        LegalAssistanceSession savedSession = sessionRepository.save(session);

        // Initial Greeting Message
        String greetingText = "Namaste " + customer.getFullName() + "! I am NyayaSetu AI Legal Assistant. I will guide you step-by-step through your legal query, gather facts, and connect you with verified advocates.";
        LegalChatMessage greetingMsg = LegalChatMessage.builder()
                .session(savedSession)
                .senderType(SenderType.AI)
                .message(greetingText)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(greetingMsg);

        LegalQuestionResponseDTO firstQuestionDTO = null;

        // If category is determined, load the first question
        LegalCategory activeCategory = savedSession.getSelectedCategory() != null ?
                savedSession.getSelectedCategory() : savedSession.getAiDetectedCategory();

        if (activeCategory != null) {
            List<String> questions = aiService.getQuestionsForCategory(activeCategory);
            savedSession.setTotalQuestions(questions.size());

            if (!questions.isEmpty()) {
                LegalQuestion q1 = LegalQuestion.builder()
                        .session(savedSession)
                        .questionNumber(1)
                        .questionText(questions.get(0))
                        .category(activeCategory)
                        .build();
                LegalQuestion savedQ1 = questionRepository.save(q1);
                savedSession.setCurrentQuestionNumber(1);

                LegalChatMessage q1Msg = LegalChatMessage.builder()
                        .session(savedSession)
                        .senderType(SenderType.AI)
                        .message("Question 1 of " + questions.size() + ": " + questions.get(0))
                        .messageType(MessageType.QUESTION)
                        .build();
                chatMessageRepository.save(q1Msg);

                firstQuestionDTO = toQuestionDTO(savedQ1);
            }
            sessionRepository.save(savedSession);
        }

        log.info("AI Legal Session started: sessionId={}, customerId={}, category={}",
                savedSession.getId(), customerId, activeCategory);

        return StartLegalSessionResponseDTO.builder()
                .sessionId(savedSession.getId())
                .selectedCategory(savedSession.getSelectedCategory())
                .categoryDisplayName(savedSession.getSelectedCategory() != null ? savedSession.getSelectedCategory().getDisplayName() : null)
                .aiDetectedCategory(savedSession.getAiDetectedCategory())
                .practiceArea(savedSession.getPracticeArea())
                .status(savedSession.getStatus())
                .currentQuestionNumber(savedSession.getCurrentQuestionNumber())
                .totalQuestions(savedSession.getTotalQuestions())
                .initialMessage(toChatMessageDTO(greetingMsg))
                .firstQuestion(firstQuestionDTO)
                .createdAt(savedSession.getCreatedAt())
                .build();
    }

    // ─── 2. SEND CUSTOMER MESSAGE ──────────────────────────────────────────────

    @Override
    @Transactional
    public LegalChatMessageResponseDTO sendCustomerMessage(Long customerId, Long sessionId, SendLegalMessageRequestDTO request) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);

        // Record customer message
        LegalChatMessage userMsg = LegalChatMessage.builder()
                .session(session)
                .senderType(SenderType.CUSTOMER)
                .message(request.getMessage())
                .messageType(MessageType.TEXT)
                .build();
        LegalChatMessage savedUserMsg = chatMessageRepository.save(userMsg);

        // If category is not yet identified, detect from user message
        if (session.getSelectedCategory() == null && session.getAiDetectedCategory() == null) {
            LegalCategoryResultResponseDTO detection = aiService.detectCategoryFromText(request.getMessage(), sessionId);
            session.setAiDetectedCategory(detection.getLikelyLegalCategory());
            session.setPracticeArea(detection.getRelevantPracticeArea());
            session.setInitialProblemDescription(request.getMessage());
            session.setStatus(LegalSessionStatus.QUESTIONING);

            List<String> questions = aiService.getQuestionsForCategory(detection.getLikelyLegalCategory());
            session.setTotalQuestions(questions.size());

            // Post Category Detection Result Message
            String catResultMsg = "Based on your description, this matter falls under: " +
                    detection.getCategoryDisplayName() + " (" + detection.getPracticeAreaDisplayName() + ").\n" +
                    "I will now ask " + questions.size() + " key questions to structure your case.";

            LegalChatMessage catMsg = LegalChatMessage.builder()
                    .session(session)
                    .senderType(SenderType.AI)
                    .message(catResultMsg)
                    .messageType(MessageType.CATEGORY_RESULT)
                    .build();
            chatMessageRepository.save(catMsg);

            // Post Question 1
            if (!questions.isEmpty()) {
                LegalQuestion q1 = LegalQuestion.builder()
                        .session(session)
                        .questionNumber(1)
                        .questionText(questions.get(0))
                        .category(detection.getLikelyLegalCategory())
                        .build();
                questionRepository.save(q1);
                session.setCurrentQuestionNumber(1);

                LegalChatMessage qMsg = LegalChatMessage.builder()
                        .session(session)
                        .senderType(SenderType.AI)
                        .message("Question 1 of " + questions.size() + ": " + questions.get(0))
                        .messageType(MessageType.QUESTION)
                        .build();
                chatMessageRepository.save(qMsg);
            }
            sessionRepository.save(session);
        }

        return toChatMessageDTO(savedUserMsg);
    }

    // ─── 3. ANSWER QUESTION & ADVANCE ──────────────────────────────────────────

    @Override
    @Transactional
    public LegalQuestionResponseDTO answerQuestion(Long customerId, Long sessionId, LegalAnswerRequestDTO request) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);

        LegalQuestion question;
        if (request.getQuestionId() != null) {
            question = questionRepository.findById(request.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + request.getQuestionId()));
        } else {
            question = questionRepository.findBySessionAndQuestionNumber(session, session.getCurrentQuestionNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Active question not found for number: " + session.getCurrentQuestionNumber()));
        }

        // Save Answer
        LegalAnswer answer = LegalAnswer.builder()
                .session(session)
                .question(question)
                .answerText(request.getAnswerText())
                .build();
        answerRepository.save(answer);

        // Record in Chat
        LegalChatMessage ansChat = LegalChatMessage.builder()
                .session(session)
                .senderType(SenderType.CUSTOMER)
                .message(request.getAnswerText())
                .messageType(MessageType.ANSWER)
                .build();
        chatMessageRepository.save(ansChat);

        // Next Question Sequencing
        LegalCategory activeCategory = session.getAiDetectedCategory() != null ?
                session.getAiDetectedCategory() : session.getSelectedCategory();
        if (activeCategory == null) {
            activeCategory = LegalCategory.OTHER;
        }

        List<String> allQuestions = aiService.getQuestionsForCategory(activeCategory);
        int nextQNumber = question.getQuestionNumber() + 1;

        if (nextQNumber <= allQuestions.size()) {
            // Create and persist next question
            LegalQuestion nextQ = LegalQuestion.builder()
                    .session(session)
                    .questionNumber(nextQNumber)
                    .questionText(allQuestions.get(nextQNumber - 1))
                    .category(activeCategory)
                    .build();
            LegalQuestion savedNextQ = questionRepository.save(nextQ);
            session.setCurrentQuestionNumber(nextQNumber);

            LegalChatMessage nextQChat = LegalChatMessage.builder()
                    .session(session)
                    .senderType(SenderType.AI)
                    .message("Question " + nextQNumber + " of " + allQuestions.size() + ": " + nextQ.getQuestionText())
                    .messageType(MessageType.QUESTION)
                    .build();
            chatMessageRepository.save(nextQChat);
            sessionRepository.save(session);

            return toQuestionDTO(savedNextQ);
        } else {
            // All questions answered — Complete session & generate summary + lawyer suggestions
            session.setStatus(LegalSessionStatus.LAWYERS_SUGGESTED);
            List<LegalAnswer> answers = answerRepository.findBySessionOrderByCreatedAtAsc(session);
            String caseSummary = aiService.generateCaseSummary(session, answers);
            session.setSummary(caseSummary);
            sessionRepository.save(session);

            // Post Summary Message to Chat
            LegalChatMessage summaryChat = LegalChatMessage.builder()
                    .session(session)
                    .senderType(SenderType.AI)
                    .message(caseSummary)
                    .messageType(MessageType.SUMMARY)
                    .build();
            chatMessageRepository.save(summaryChat);

            // Automatically match and save verified lawyers
            PracticeArea practiceArea = session.getPracticeArea() != null ?
                    session.getPracticeArea() : aiService.mapCategoryToPracticeArea(activeCategory);
            lawyerMatchingService.matchAndSaveLawyers(session, practiceArea);

            log.info("AI Legal Session completed: sessionId={}, matched lawyers generated", sessionId);
            return null; // Signals all questions are completed
        }
    }

    // ─── 4. UPLOAD DOCUMENT ────────────────────────────────────────────────────

    @Override
    @Transactional
    public LegalDocumentResponseDTO uploadDocument(Long customerId, Long sessionId, LegalDocumentType documentType, MultipartFile file) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);
        Customer customer = session.getCustomer();

        if (documentType == null) {
            documentType = LegalDocumentType.OTHER;
        }

        String fileUrl;
        try {
            fileUrl = fileStorageService.storeCustomerLegalDocument(customerId, sessionId, file);
        } catch (IOException e) {
            log.error("Failed to store customer document for sessionId={}", sessionId, e);
            throw new RuntimeException("Failed to upload document. Please try again.");
        }

        String storedFileName = fileStorageService.getStoredFileName(fileUrl);

        LegalAssistanceDocument doc = LegalAssistanceDocument.builder()
                .session(session)
                .customer(customer)
                .documentType(documentType)
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .fileUrl(fileUrl)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .build();

        LegalAssistanceDocument saved = documentRepository.save(doc);

        // Record in chat
        LegalChatMessage docMsg = LegalChatMessage.builder()
                .session(session)
                .senderType(SenderType.CUSTOMER)
                .message("Uploaded Document [" + documentType.name() + "]: " + file.getOriginalFilename())
                .messageType(MessageType.DOCUMENT)
                .build();
        chatMessageRepository.save(docMsg);

        log.info("Document uploaded for legal session: id={}, type={}, url={}", saved.getId(), documentType, fileUrl);
        return toDocumentDTO(saved);
    }

    // ─── 5. GET SESSION DETAILS & MESSAGES ─────────────────────────────────────

    @Override
    public LegalSessionDetailResponseDTO getSessionDetail(Long customerId, Long sessionId) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);
        return toSessionDetailDTO(session);
    }

    @Override
    public List<LegalChatMessageResponseDTO> getSessionMessages(Long customerId, Long sessionId) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);
        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session).stream()
                .map(this::toChatMessageDTO)
                .collect(Collectors.toList());
    }

    @Override
    public LegalSessionSummaryResponseDTO getSessionSummary(Long customerId, Long sessionId) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);
        LegalCategory category = session.getAiDetectedCategory() != null ?
                session.getAiDetectedCategory() : session.getSelectedCategory();

        int questionsCount = answerRepository.findBySessionOrderByCreatedAtAsc(session).size();
        int docsCount = documentRepository.findBySession(session).size();
        List<LawyerSuggestionResponseDTO> lawyers = lawyerMatchingService.getSuggestionsForSession(session);

        return LegalSessionSummaryResponseDTO.builder()
                .sessionId(session.getId())
                .likelyLegalCategory(category)
                .categoryDisplayName(category != null ? category.getDisplayName() : "General Legal Matter")
                .relevantPracticeArea(session.getPracticeArea())
                .practiceAreaDisplayName(session.getPracticeArea() != null ? session.getPracticeArea().name().replace("_", " ") : "Civil Disputes")
                .summary(session.getSummary() != null ? session.getSummary() : "Assessment in progress.")
                .status(session.getStatus())
                .totalQuestionsAnswered(questionsCount)
                .documentsUploadedCount(docsCount)
                .suggestedLawyers(lawyers)
                .disclaimer(AiLegalAssistantServiceImpl.LEGAL_DISCLAIMER)
                .build();
    }

    @Override
    public List<LawyerSuggestionResponseDTO> getMatchingLawyers(Long customerId, Long sessionId) {
        LegalAssistanceSession session = getSessionForCustomer(customerId, sessionId);
        List<LawyerSuggestionResponseDTO> suggestions = lawyerMatchingService.getSuggestionsForSession(session);

        if (suggestions.isEmpty() && session.getPracticeArea() != null) {
            suggestions = lawyerMatchingService.matchAndSaveLawyers(session, session.getPracticeArea());
        }
        return suggestions;
    }

    @Override
    public List<LegalSessionDetailResponseDTO> getMySessions(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        return sessionRepository.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .map(this::toSessionDetailDTO)
                .collect(Collectors.toList());
    }

    // ─── 6. ADMIN OPERATIONS ───────────────────────────────────────────────────

    @Override
    public List<LegalSessionDetailResponseDTO> getAllSessionsForAdmin() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSessionDetailDTO)
                .collect(Collectors.toList());
    }

    @Override
    public LegalSessionDetailResponseDTO getSessionDetailForAdmin(Long sessionId) {
        LegalAssistanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal assistance session not found with ID: " + sessionId));
        return toSessionDetailDTO(session);
    }

    @Override
    public List<LegalDocumentResponseDTO> getSessionDocumentsForAdmin(Long sessionId) {
        LegalAssistanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal assistance session not found with ID: " + sessionId));
        return documentRepository.findBySession(session).stream()
                .map(this::toDocumentDTO)
                .collect(Collectors.toList());
    }

    // ─── HELPER METHODS ────────────────────────────────────────────────────────

    private LegalAssistanceSession getSessionForCustomer(Long customerId, Long sessionId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        return sessionRepository.findByIdAndCustomer(sessionId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Legal assistance session not found or does not belong to you: " + sessionId));
    }

    private LegalChatMessageResponseDTO toChatMessageDTO(LegalChatMessage msg) {
        return LegalChatMessageResponseDTO.builder()
                .id(msg.getId())
                .sessionId(msg.getSession().getId())
                .senderType(msg.getSenderType())
                .message(msg.getMessage())
                .messageType(msg.getMessageType())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private LegalQuestionResponseDTO toQuestionDTO(LegalQuestion q) {
        return LegalQuestionResponseDTO.builder()
                .id(q.getId())
                .sessionId(q.getSession().getId())
                .questionNumber(q.getQuestionNumber())
                .questionText(q.getQuestionText())
                .category(q.getCategory())
                .categoryDisplayName(q.getCategory() != null ? q.getCategory().getDisplayName() : null)
                .createdAt(q.getCreatedAt())
                .build();
    }

    private LegalDocumentResponseDTO toDocumentDTO(LegalAssistanceDocument doc) {
        return LegalDocumentResponseDTO.builder()
                .id(doc.getId())
                .sessionId(doc.getSession().getId())
                .documentType(doc.getDocumentType())
                .originalFileName(doc.getOriginalFileName())
                .fileUrl(doc.getFileUrl())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }

    private LegalSessionDetailResponseDTO toSessionDetailDTO(LegalAssistanceSession s) {
        List<LegalChatMessageResponseDTO> msgs = chatMessageRepository.findBySessionOrderByCreatedAtAsc(s).stream()
                .map(this::toChatMessageDTO)
                .collect(Collectors.toList());

        List<LegalDocumentResponseDTO> docs = documentRepository.findBySession(s).stream()
                .map(this::toDocumentDTO)
                .collect(Collectors.toList());

        List<LawyerSuggestionResponseDTO> lawyers = lawyerMatchingService.getSuggestionsForSession(s);

        LegalCategory category = s.getAiDetectedCategory() != null ? s.getAiDetectedCategory() : s.getSelectedCategory();

        return LegalSessionDetailResponseDTO.builder()
                .sessionId(s.getId())
                .customerId(s.getCustomer().getCustomerId())
                .customerName(s.getCustomer().getFullName())
                .customerEmail(s.getCustomer().getEmail())
                .customerMobileNumber(s.getCustomer().getMobileNumber())
                .selectedCategory(s.getSelectedCategory())
                .aiDetectedCategory(s.getAiDetectedCategory())
                .categoryDisplayName(category != null ? category.getDisplayName() : null)
                .practiceArea(s.getPracticeArea())
                .status(s.getStatus())
                .currentQuestionNumber(s.getCurrentQuestionNumber())
                .totalQuestions(s.getTotalQuestions())
                .initialProblemDescription(s.getInitialProblemDescription())
                .summary(s.getSummary())
                .messages(msgs)
                .documents(docs)
                .suggestedLawyers(lawyers)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
