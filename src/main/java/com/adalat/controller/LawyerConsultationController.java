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

    private Long getLawyerId(CustomUserDetails userDetails) {
        return userDetails != null ? userDetails.getId() : 1L;
    }

    @GetMapping
    @Operation(summary = "Get all lawyer consultations", description = "Fetch consultations with optional status filter (active, upcoming, completed, requested)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationRequestResponseDTO>>> getMyConsultations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status) {

        List<ConsultationRequestResponseDTO> list = consultationRequestService.getLawyerConsultations(getLawyerId(userDetails), status);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultations fetched successfully.", list));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get consultation details", description = "Fetch details for a specific advocate consultation")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> getConsultationDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO dto = consultationRequestService.getConsultationForLawyer(getLawyerId(userDetails), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation details fetched successfully.", dto));
    }

    @GetMapping("/{requestId}/messages")
    @Operation(summary = "Get consultation messages", description = "Fetch chat history for an advocate consultation session")
    public ResponseEntity<ApiResponseDTO<List<ConsultationChatMessageDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        List<ConsultationChatMessageDTO> messages = consultationChatService.getMessagesForLawyer(getLawyerId(userDetails), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages fetched successfully.", messages));
    }

    @PostMapping("/{requestId}/messages")
    @Operation(summary = "Send advocate consultation chat message", description = "Post an advocate chat response directly to MySQL database")
    public ResponseEntity<ApiResponseDTO<ConsultationChatMessageDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody java.util.Map<String, String> body) {

        String text = body.get("text") != null ? body.get("text") : body.get("message");
        ConsultationChatMessageDTO dto = consultationChatService.saveMessage(requestId, getLawyerId(userDetails), com.adalat.enums.SenderType.LAWYER, text);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Message saved.", dto));
    }

    @PostMapping("/{requestId}/complete")
    @Operation(summary = "Conclude consultation", description = "Mark active consultation as completed")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> completeConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO completed = consultationRequestService.completeConsultationByLawyer(getLawyerId(userDetails), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation concluded successfully.", completed));
    }
}
