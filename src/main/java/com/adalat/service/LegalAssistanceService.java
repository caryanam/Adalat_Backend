package com.adalat.service;

import com.adalat.dto.*;
import com.adalat.enums.LegalDocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LegalAssistanceService {

    StartLegalSessionResponseDTO startSession(Long customerId, StartLegalSessionRequestDTO request);

    LegalChatMessageResponseDTO sendCustomerMessage(Long customerId, Long sessionId, SendLegalMessageRequestDTO request);

    LegalQuestionResponseDTO answerQuestion(Long customerId, Long sessionId, LegalAnswerRequestDTO request);

    LegalDocumentResponseDTO uploadDocument(Long customerId, Long sessionId, LegalDocumentType documentType, MultipartFile file);

    LegalSessionDetailResponseDTO getSessionDetail(Long customerId, Long sessionId);

    List<LegalChatMessageResponseDTO> getSessionMessages(Long customerId, Long sessionId);

    LegalSessionSummaryResponseDTO getSessionSummary(Long customerId, Long sessionId);

    List<LawyerSuggestionResponseDTO> getMatchingLawyers(Long customerId, Long sessionId);

    List<LegalSessionDetailResponseDTO> getMySessions(Long customerId);

    // Admin Operations
    List<LegalSessionDetailResponseDTO> getAllSessionsForAdmin();

    LegalSessionDetailResponseDTO getSessionDetailForAdmin(Long sessionId);

    List<LegalDocumentResponseDTO> getSessionDocumentsForAdmin(Long sessionId);
}
