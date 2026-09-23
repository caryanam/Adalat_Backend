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
    private final com.adalat.service.LawyerRatingService lawyerRatingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Book advocate consultation", description = "Directly book a consultation request with an advocate")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> createConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateConsultationRequestDTO requestDTO) {

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        ConsultationRequestResponseDTO created = consultationRequestService.createRequest(customerId, requestDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation booked successfully.", created));
    }

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
    @Operation(summary = "Get consultation messages", description = "Fetch chat history for an active/completed consultation (REST API)")
    public ResponseEntity<ApiResponseDTO<List<ConsultationChatMessageDTO>>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        List<ConsultationChatMessageDTO> messages = consultationChatService.getMessagesForCustomer(customerId, requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages fetched successfully.", messages));
    }

    @PostMapping("/{requestId}/messages")
    @Operation(summary = "Send consultation chat message", description = "Post a chat message directly to MySQL database")
    public ResponseEntity<ApiResponseDTO<ConsultationChatMessageDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody java.util.Map<String, Object> body) {

        String text = body.get("text") != null ? body.get("text").toString() : (body.get("message") != null ? body.get("message").toString() : "");
        String attachmentUrl = body.get("attachmentUrl") != null ? body.get("attachmentUrl").toString() : null;
        String attachmentName = body.get("attachmentName") != null ? body.get("attachmentName").toString() : null;
        String attachmentType = body.get("attachmentType") != null ? body.get("attachmentType").toString() : null;
        Long attachmentSize = body.get("attachmentSize") != null ? Long.valueOf(body.get("attachmentSize").toString()) : null;

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        try {
            ConsultationChatMessageDTO dto = consultationChatService.saveMessage(
                    requestId, customerId, com.adalat.enums.SenderType.CUSTOMER, text,
                    attachmentUrl, attachmentName, attachmentType, attachmentSize);
            return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Message saved.", dto));
        } catch (IllegalStateException e) {
            if ("FREE_CHAT_OVER".equals(e.getMessage())) {
                return ResponseEntity.status(403).body(new ApiResponseDTO<>("FREE_CHAT_OVER", "Free chat limit (2 mins) exceeded. Payment required.", null));
            }
            throw e;
        }
    }

    @PostMapping(value = "/{requestId}/upload-attachment", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and send consultation chat attachment", description = "Uploads file to server and posts message with attachment to MySQL")
    public ResponseEntity<ApiResponseDTO<ConsultationChatMessageDTO>> uploadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "message", required = false) String message) {

        String messageText = text != null ? text : (message != null ? message : "");
        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        try {
            ConsultationChatMessageDTO dto = consultationChatService.uploadAndSaveAttachment(
                    requestId, customerId, com.adalat.enums.SenderType.CUSTOMER, file, messageText);
            return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Attachment uploaded and message sent.", dto));
        } catch (IllegalStateException e) {
            if ("FREE_CHAT_OVER".equals(e.getMessage())) {
                return ResponseEntity.status(403).body(new ApiResponseDTO<>("FREE_CHAT_OVER", "Free chat limit (2 mins) exceeded. Payment required.", null));
            }
            throw e;
        }
    }

    @PostMapping("/{requestId}/messages/seen")
    @Operation(summary = "Mark consultation messages as seen", description = "Updates status of advocate messages to SEEN in MySQL database")
    public ResponseEntity<ApiResponseDTO<List<Long>>> markMessagesSeen(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        List<Long> messageIds = null;
        if (body != null && body.containsKey("messageIds") && body.get("messageIds") instanceof List<?> list) {
            messageIds = list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        List<Long> updated = consultationChatService.markMessagesSeen(requestId, customerId, com.adalat.enums.SenderType.CUSTOMER, messageIds);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages marked as seen.", updated));
    }

    @PostMapping("/{requestId}/messages/delivered")
    @Operation(summary = "Mark consultation messages as delivered", description = "Updates status of advocate messages to DELIVERED in MySQL database")
    public ResponseEntity<ApiResponseDTO<List<Long>>> markMessagesDelivered(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        List<Long> messageIds = null;
        if (body != null && body.containsKey("messageIds") && body.get("messageIds") instanceof List<?> list) {
            messageIds = list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        List<Long> updated = consultationChatService.markMessagesDelivered(requestId, customerId, com.adalat.enums.SenderType.CUSTOMER, messageIds);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages marked as delivered.", updated));
    }

    @PostMapping("/{requestId}/complete")
    @Operation(summary = "Conclude consultation", description = "Mark active consultation as completed")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> completeConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        Long customerId = userDetails != null ? userDetails.getId() : 1L;
        ConsultationRequestResponseDTO completed = consultationRequestService.completeConsultationByCustomer(customerId, requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation marked as completed successfully.", completed));
    }

    @PostMapping("/{requestId}/unlock")
    @Operation(summary = "Unlock paid consultation", description = "Activates consultation session after payment")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> unlockConsultation(
            @PathVariable Long requestId,
            @RequestBody(required = false) java.util.Map<String, String> body) {

        String paymentId = body != null ? body.get("paymentId") : "PAY_MOCK_" + System.currentTimeMillis();
        String amount = body != null ? body.get("amount") : null;
        ConsultationRequestResponseDTO response = consultationRequestService.unlockPaidConsultation(requestId, paymentId, amount);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Paid consultation unlocked successfully.", response));
    }

    @PostMapping("/{requestId}/rating")
    @Operation(summary = "Submit rating & feedback for advocate consultation", description = "Submits a rating (1-5) and feedback review for the consultation and recalculates advocate average rating in database")
    public ResponseEntity<ApiResponseDTO<LawyerReviewResponseDTO>> rateConsultation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @Valid @RequestBody LawyerReviewRequestDTO requestDTO) {

        Long customerId = userDetails != null ? userDetails.getId() : null;
        LawyerReviewResponseDTO review = lawyerRatingService.submitConsultationRating(customerId, requestId, requestDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Thank you! Your rating and review have been recorded.", review));
    }
}
