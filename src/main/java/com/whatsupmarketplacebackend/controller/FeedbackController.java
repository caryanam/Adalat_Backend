package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.FeedbackRequestDTO;
import com.whatsupmarketplacebackend.dto.FeedbackResponseDTO;
import com.whatsupmarketplacebackend.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feedback Management", description = "Endpoints for Client feedback & testimonial operations")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/create/{clientId}")
    @Operation(summary = "Submit new feedback/testimonial for a registered client", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feedback submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or rating outside scale 1-5"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Client not found in database")
    })
    public ResponseEntity<ApiResponseDTO<FeedbackResponseDTO>> createFeedback(
            @PathVariable Long clientId,
            @Valid @RequestBody FeedbackRequestDTO requestDTO) {
        log.info("Received feedback submission from client id: {}", clientId);
        FeedbackResponseDTO responseDTO = feedbackService.createFeedback(clientId, requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.<FeedbackResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Feedback submitted successfully. It will be published after admin approval.")
                        .data(responseDTO)
                        .build());
    }

    @GetMapping("/all")
    @Operation(summary = "Get all feedback entries", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all feedback")
    })
    public ResponseEntity<ApiResponseDTO<List<FeedbackResponseDTO>>> getAllFeedback() {
        log.info("Fetching all feedback");
        List<FeedbackResponseDTO> feedbackList = feedbackService.getAllFeedback();

        return ResponseEntity.ok(
                ApiResponseDTO.<List<FeedbackResponseDTO>>builder()
                        .status("SUCCESS")
                        .message("All feedback retrieved successfully")
                        .data(feedbackList)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get feedback details by ID", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved feedback"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<ApiResponseDTO<FeedbackResponseDTO>> getFeedbackById(@PathVariable Long id) {
        log.info("Fetching feedback by id: {}", id);
        FeedbackResponseDTO feedback = feedbackService.getFeedbackById(id);

        return ResponseEntity.ok(
                ApiResponseDTO.<FeedbackResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Feedback retrieved successfully")
                        .data(feedback)
                        .build()
        );
    }

    @PutMapping("/update/{feedbackId}")
    @Operation(summary = "Update feedback entry", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<ApiResponseDTO<FeedbackResponseDTO>> updateFeedback(
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackRequestDTO requestDTO) {
        log.info("Updating feedback id: {}", feedbackId);
        FeedbackResponseDTO responseDTO = feedbackService.updateFeedback(feedbackId, requestDTO);

        return ResponseEntity.ok(
                ApiResponseDTO.<FeedbackResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Feedback updated successfully")
                        .data(responseDTO)
                        .build()
        );
    }

    @DeleteMapping("/delete/{feedbackId}")
    @Operation(summary = "Delete feedback entry", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<ApiResponseDTO<Object>> deleteFeedback(@PathVariable Long feedbackId) {
        log.info("Deleting feedback id: {}", feedbackId);
        feedbackService.deleteFeedback(feedbackId);

        return ResponseEntity.ok(
                ApiResponseDTO.<Object>builder()
                        .status("SUCCESS")
                        .message("Feedback deleted successfully")
                        .data(null)
                        .build()
        );
    }
}
