package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.ConsultationChatMessageDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ConsultationChatService;
import com.adalat.service.ConsultationRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lawyer/consultations")
@RequiredArgsConstructor
@Tag(name = "Lawyer Consultations", description = "Endpoints for advocates to view and manage active/completed consultation sessions")
public class LawyerConsultationController {

    private final ConsultationRequestService consultationRequestService;
    private final ConsultationChatService consultationChatService;

    @GetMapping
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Get all lawyer consultations", description = "Fetch consultations with optional status filter (active, upcoming, completed, requested)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationRequestResponseDTO>>> getMyConsultations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status) {

        List<ConsultationRequestResponseDTO> list = consultationRequestService.getLawyerConsultations(userDetails.getId(), status);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultations fetched successfully.", list));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Get consultation details", description = "Fetch details for a specific advocate consultation")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> getConsultationDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO dto = consultationRequestService.getConsultationForLawyer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation details fetched successfully.", dto));
    }

    @GetMapping("/{requestId}/messages")
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Get consultation messages", description = "Fetch chat history for an advocate consultation session (REST fallback)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationChatMessageDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        List<ConsultationChatMessageDTO> messages = consultationChatService.getMessagesForLawyer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages fetched successfully.", messages));
    }

    @PostMapping("/{requestId}/complete")
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Conclude consultation", description = "Mark active consultation as completed")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> completeConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO completed = consultationRequestService.completeConsultationByLawyer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation concluded successfully.", completed));
    }
}
