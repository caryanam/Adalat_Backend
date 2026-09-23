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
            @RequestBody java.util.Map<String, Object> body) {

        String text = body.get("text") != null ? body.get("text").toString() : (body.get("message") != null ? body.get("message").toString() : "");
        String attachmentUrl = body.get("attachmentUrl") != null ? body.get("attachmentUrl").toString() : null;
        String attachmentName = body.get("attachmentName") != null ? body.get("attachmentName").toString() : null;
        String attachmentType = body.get("attachmentType") != null ? body.get("attachmentType").toString() : null;
        Long attachmentSize = body.get("attachmentSize") != null ? Long.valueOf(body.get("attachmentSize").toString()) : null;

        ConsultationChatMessageDTO dto = consultationChatService.saveMessage(
                requestId, getLawyerId(userDetails), com.adalat.enums.SenderType.LAWYER, text,
                attachmentUrl, attachmentName, attachmentType, attachmentSize);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Message saved.", dto));
    }

    @PostMapping(value = "/{requestId}/upload-attachment", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and send advocate consultation chat attachment", description = "Uploads file to server and posts message with attachment to MySQL")
    public ResponseEntity<ApiResponseDTO<ConsultationChatMessageDTO>> uploadAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "message", required = false) String message) {

        String messageText = text != null ? text : (message != null ? message : "");
        ConsultationChatMessageDTO dto = consultationChatService.uploadAndSaveAttachment(
                requestId, getLawyerId(userDetails), com.adalat.enums.SenderType.LAWYER, file, messageText);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Attachment uploaded and message sent.", dto));
    }

    @PostMapping("/{requestId}/messages/seen")
    @Operation(summary = "Mark customer consultation messages as seen", description = "Updates status of customer messages to SEEN in MySQL database")
    public ResponseEntity<ApiResponseDTO<List<Long>>> markMessagesSeen(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {

        List<Long> messageIds = null;
        if (body != null && body.containsKey("messageIds") && body.get("messageIds") instanceof List<?> list) {
            messageIds = list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        List<Long> updated = consultationChatService.markMessagesSeen(requestId, getLawyerId(userDetails), com.adalat.enums.SenderType.LAWYER, messageIds);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages marked as seen.", updated));
    }

    @PostMapping("/{requestId}/messages/delivered")
    @Operation(summary = "Mark customer consultation messages as delivered", description = "Updates status of customer messages to DELIVERED in MySQL database")
    public ResponseEntity<ApiResponseDTO<List<Long>>> markMessagesDelivered(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {

        List<Long> messageIds = null;
        if (body != null && body.containsKey("messageIds") && body.get("messageIds") instanceof List<?> list) {
            messageIds = list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        List<Long> updated = consultationChatService.markMessagesDelivered(requestId, getLawyerId(userDetails), com.adalat.enums.SenderType.LAWYER, messageIds);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Messages marked as delivered.", updated));
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
