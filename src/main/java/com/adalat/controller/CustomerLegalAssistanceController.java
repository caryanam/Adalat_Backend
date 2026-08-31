package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.enums.LegalDocumentType;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ConsultationRequestService;
import com.adalat.service.LegalAssistanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/customer/legal-assistance")
@RequiredArgsConstructor
public class CustomerLegalAssistanceController {

    private final LegalAssistanceService legalAssistanceService;
    private final ConsultationRequestService consultationRequestService;

    // ─── 1. START SESSION ──────────────────────────────────────────────────────
    @PostMapping("/start")
    public ResponseEntity<ApiResponseDTO<StartLegalSessionResponseDTO>> startSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) StartLegalSessionRequestDTO request) {

        StartLegalSessionResponseDTO response = legalAssistanceService.startSession(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>("SUCCESS", "AI Legal Assistant session created successfully.", response));
    }

    // ─── 2. SEND MESSAGE / DESCRIBE PROBLEM ────────────────────────────────────
    @PostMapping("/{sessionId}/message")
    public ResponseEntity<ApiResponseDTO<LegalChatMessageResponseDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendLegalMessageRequestDTO request) {

        LegalChatMessageResponseDTO response = legalAssistanceService.sendCustomerMessage(userDetails.getId(), sessionId, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Message sent.", response));
    }

    // ─── 3. ANSWER QUESTION ────────────────────────────────────────────────────
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ApiResponseDTO<LegalQuestionResponseDTO>> answerQuestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody LegalAnswerRequestDTO request) {

        LegalQuestionResponseDTO nextQuestion = legalAssistanceService.answerQuestion(userDetails.getId(), sessionId, request);
        String message = nextQuestion != null ? "Answer saved. Next question loaded." : "All questions completed. Case summary and lawyer suggestions are now ready!";
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", message, nextQuestion));
    }

    // ─── 4. UPLOAD DOCUMENT ────────────────────────────────────────────────────
    @PostMapping("/{sessionId}/documents")
    public ResponseEntity<ApiResponseDTO<LegalDocumentResponseDTO>> uploadDocument(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @RequestParam("documentType") LegalDocumentType documentType,
            @RequestParam("file") MultipartFile file) {

        LegalDocumentResponseDTO response = legalAssistanceService.uploadDocument(userDetails.getId(), sessionId, documentType, file);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Document uploaded successfully.", response));
    }

    // ─── 5. GET SESSION DETAILS ────────────────────────────────────────────────
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponseDTO<LegalSessionDetailResponseDTO>> getSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        LegalSessionDetailResponseDTO response = legalAssistanceService.getSessionDetail(userDetails.getId(), sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Session details retrieved.", response));
    }

    // ─── 6. GET CHAT MESSAGES ──────────────────────────────────────────────────
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponseDTO<List<LegalChatMessageResponseDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        List<LegalChatMessageResponseDTO> messages = legalAssistanceService.getSessionMessages(userDetails.getId(), sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Session messages retrieved.", messages));
    }

    // ─── 7. GET CASE SUMMARY ───────────────────────────────────────────────────
    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<ApiResponseDTO<LegalSessionSummaryResponseDTO>> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        LegalSessionSummaryResponseDTO summary = legalAssistanceService.getSessionSummary(userDetails.getId(), sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Case summary retrieved.", summary));
    }

    // ─── 8. GET MATCHING LAWYERS ───────────────────────────────────────────────
    @GetMapping("/{sessionId}/lawyers")
    public ResponseEntity<ApiResponseDTO<List<LawyerSuggestionResponseDTO>>> getMatchingLawyers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        List<LawyerSuggestionResponseDTO> lawyers = legalAssistanceService.getMatchingLawyers(userDetails.getId(), sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Matching verified lawyers retrieved.", lawyers));
    }

    // ─── 9. REQUEST LAWYER CONSULTATION ────────────────────────────────────────
    @PostMapping("/{sessionId}/lawyers/{lawyerId}/request")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> requestLawyer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @PathVariable Long lawyerId) {

        ConsultationRequestResponseDTO response = consultationRequestService.createRequest(userDetails.getId(), sessionId, lawyerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>("SUCCESS", "Consultation request sent to advocate.", response));
    }

    // ─── 10. GET MY SESSIONS ───────────────────────────────────────────────────
    @GetMapping("/my-sessions")
    public ResponseEntity<ApiResponseDTO<List<LegalSessionDetailResponseDTO>>> getMySessions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<LegalSessionDetailResponseDTO> sessions = legalAssistanceService.getMySessions(userDetails.getId());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "My legal assistance sessions retrieved.", sessions));
    }
}
