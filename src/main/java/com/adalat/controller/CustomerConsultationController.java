package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ConsultationChatService;
import com.adalat.service.ConsultationRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/consultations")
@RequiredArgsConstructor
@Tag(name = "Customer Consultations", description = "Endpoints for customers to view, pay, and manage advocate consultations")
public class CustomerConsultationController {

    private final ConsultationRequestService consultationRequestService;
    private final ConsultationChatService consultationChatService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get all customer consultations", description = "Fetch consultations with optional status filter (active, upcoming, completed, cancelled)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationRequestResponseDTO>>> getMyConsultations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status) {

        List<ConsultationRequestResponseDTO> list = consultationRequestService.getCustomerConsultations(userDetails.getId(), status);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultations fetched successfully.", list));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get consultation details", description = "Fetch detailed status and info for a specific consultation")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> getConsultationDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO dto = consultationRequestService.getConsultationForCustomer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation details fetched successfully.", dto));
    }

    @PostMapping("/{requestId}/payment/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Initiate consultation payment", description = "Generates a payment order for the advocate's consultation fee")
    public ResponseEntity<ApiResponseDTO<ConsultationPaymentInitiateDTO>> initiatePayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationPaymentInitiateDTO response = consultationRequestService.initiateConsultationPayment(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Payment initiated successfully.", response));
    }

    @PostMapping("/{requestId}/payment/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Verify consultation payment", description = "Verifies payment and activates the real-time chat session")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> verifyPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @Valid @RequestBody ConsultationPaymentVerifyDTO verifyDTO) {

        ConsultationRequestResponseDTO updated = consultationRequestService.verifyConsultationPayment(userDetails.getId(), requestId, verifyDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Payment verified successfully. Consultation is now active.", updated));
    }

    @GetMapping("/{requestId}/messages")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get consultation messages", description = "Fetch chat history for an active/completed consultation (REST fallback)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationChatMessageDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        List<ConsultationChatMessageDTO> messages = consultationChatService.getMessagesForCustomer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages fetched successfully.", messages));
    }

    @PostMapping("/{requestId}/complete")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Conclude consultation", description = "Mark active consultation as completed")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> completeConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        ConsultationRequestResponseDTO completed = consultationRequestService.completeConsultationByCustomer(userDetails.getId(), requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation marked as completed successfully.", completed));
    }
}
