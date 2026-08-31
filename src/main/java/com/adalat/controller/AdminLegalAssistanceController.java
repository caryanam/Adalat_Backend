package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.LegalDocumentResponseDTO;
import com.adalat.dto.LegalSessionDetailResponseDTO;
import com.adalat.service.LegalAssistanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/legal-assistance")
@RequiredArgsConstructor
public class AdminLegalAssistanceController {

    private final LegalAssistanceService legalAssistanceService;

    // ─── 1. GET ALL LEGAL SESSIONS (ADMIN) ────────────────────────────────────
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponseDTO<List<LegalSessionDetailResponseDTO>>> getAllSessions() {
        List<LegalSessionDetailResponseDTO> sessions = legalAssistanceService.getAllSessionsForAdmin();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "All customer legal assistance sessions retrieved.", sessions));
    }

    // ─── 2. GET SPECIFIC SESSION DETAIL (ADMIN) ───────────────────────────────
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponseDTO<LegalSessionDetailResponseDTO>> getSessionDetail(
            @PathVariable Long sessionId) {

        LegalSessionDetailResponseDTO session = legalAssistanceService.getSessionDetailForAdmin(sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Legal assistance session detail retrieved.", session));
    }

    // ─── 3. GET UPLOADED DOCUMENTS (ADMIN) ────────────────────────────────────
    @GetMapping("/sessions/{sessionId}/documents")
    public ResponseEntity<ApiResponseDTO<List<LegalDocumentResponseDTO>>> getSessionDocuments(
            @PathVariable Long sessionId) {

        List<LegalDocumentResponseDTO> documents = legalAssistanceService.getSessionDocumentsForAdmin(sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Legal session documents retrieved.", documents));
    }
}
