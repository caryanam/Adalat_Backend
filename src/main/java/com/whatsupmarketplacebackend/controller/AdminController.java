package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.ExcelImportResponseDTO;
import com.whatsupmarketplacebackend.dto.FeedbackApprovalRequestDTO;
import com.whatsupmarketplacebackend.dto.FeedbackResponseDTO;
import com.whatsupmarketplacebackend.service.ClientService;
import com.whatsupmarketplacebackend.service.CustomerDataService;
import com.whatsupmarketplacebackend.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Removed old subscription imports
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Management", description = "Endpoints for Admin management operations")
public class AdminController {

    private final FeedbackService feedbackService;
    private final CustomerDataService customerDataService;
    private final ClientService clientService;

    @GetMapping("/clients")
    @Operation(summary = "Get all clients for Admin Dashboard (Ordered by Latest First)", description = "Access Level: Protected [Required Role: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clients fetched successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<ApiResponseDTO<List<com.whatsupmarketplacebackend.dto.ClientResponseDTO>>> getAllClientsForDashboard() {
        log.info("Admin fetching all clients for dashboard");
        return ResponseEntity.ok(
                ApiResponseDTO.<List<com.whatsupmarketplacebackend.dto.ClientResponseDTO>>builder()
                        .status("SUCCESS")
                        .message("Clients fetched successfully")
                        .data(clientService.getAllClientsForDashboard())
                        .build()
        );
    }





    @PatchMapping("/feedback/approve-reject")
    @Operation(summary = "Approve or Reject feedback", description = "Access Level: Protected [Required Role: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<ApiResponseDTO<FeedbackResponseDTO>> approveOrRejectFeedback(
            @Valid @RequestBody FeedbackApprovalRequestDTO requestDTO) {
        log.info("Admin updating feedback status for feedbackId: {} to {}", requestDTO.feedbackId(), requestDTO.status());
        FeedbackResponseDTO responseDTO = feedbackService.updateStatus(requestDTO.feedbackId(), requestDTO.status());

        return ResponseEntity.ok(
                ApiResponseDTO.<FeedbackResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Feedback status updated successfully to " + requestDTO.status())
                        .data(responseDTO)
                        .build()
        );
    }

}
