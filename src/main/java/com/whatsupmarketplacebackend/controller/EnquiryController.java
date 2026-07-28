package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.EnquiryRequestDTO;
import com.whatsupmarketplacebackend.dto.EnquiryResponseDTO;
import com.whatsupmarketplacebackend.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/enquirie")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Enquiry Management", description = "Endpoints for public enquiry form submissions and enquiry lookup")
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping("/create")
    @Operation(summary = "Submit a new enquiry form", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Enquiry submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<ApiResponseDTO<EnquiryResponseDTO>> submitEnquiry(@Valid @RequestBody EnquiryRequestDTO requestDTO) {
        log.info("Received enquiry submission for email: {}", requestDTO.email());
        EnquiryResponseDTO responseDTO = enquiryService.createEnquiry(requestDTO);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.<EnquiryResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Enquiry submitted successfully")
                        .data(responseDTO)
                        .build());
    }

    @GetMapping("/all")
    @Operation(summary = "Get all enquiries", description = "Access Level: Protected [Required Token: Authenticated User]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all enquiries"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required")
    })
    public ResponseEntity<ApiResponseDTO<List<EnquiryResponseDTO>>> getAllEnquiries() {
        log.info("Fetching all enquiries");
        List<EnquiryResponseDTO> enquiries = enquiryService.getAllEnquiries();
        
        return ResponseEntity.ok(
                ApiResponseDTO.<List<EnquiryResponseDTO>>builder()
                        .status("SUCCESS")
                        .message("Enquiries retrieved successfully")
                        .data(enquiries)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an enquiry by ID", description = "Access Level: Protected [Required Token: Authenticated User]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved enquiry"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Enquiry not found")
    })
    public ResponseEntity<ApiResponseDTO<EnquiryResponseDTO>> getEnquiryById(@PathVariable Long id) {
        log.info("Fetching enquiry by id: {}", id);
        EnquiryResponseDTO enquiry = enquiryService.getEnquiryById(id);
        
        return ResponseEntity.ok(
                ApiResponseDTO.<EnquiryResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Enquiry retrieved successfully")
                        .data(enquiry)
                        .build()
        );
    }
}
