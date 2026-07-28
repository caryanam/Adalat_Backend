package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.ExcelImportResponseDTO;
import com.whatsupmarketplacebackend.dto.CustomerDataResponseDTO;
import com.whatsupmarketplacebackend.service.CustomerDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/customer-data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Data Management", description = "Endpoints for Customer Data import and retrieval (Accessible by CLIENT and ADMIN)")
public class CustomerDataController {

    private final CustomerDataService customerDataService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import customer data from an excel sheet (.xlsx/.xls)", description = "Access Level: Protected [Required Role: ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Excel data imported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or empty file uploaded"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<ApiResponseDTO<ExcelImportResponseDTO>> importCustomerData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") Long clientId,
            @RequestParam("businessCategory") com.whatsupmarketplacebackend.enums.BusinessCategory businessCategory) {

        log.info("User importing customer data from file: {} for clientId: {} and category: {}", file.getOriginalFilename(), clientId, businessCategory);

        ExcelImportResponseDTO responseDTO = customerDataService.importFromExcel(file, clientId, businessCategory);

        return ResponseEntity.ok(
                ApiResponseDTO.<ExcelImportResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Customer data imported successfully")
                        .data(responseDTO)
                        .build()
        );
    }

    @GetMapping("/client/{clientId}")
    @Operation(summary = "Get customer data by client ID", description = "Access Level: Protected [Required Role: ADMIN, CLIENT]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer data fetched successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or Client role required")
    })
    public ResponseEntity<ApiResponseDTO<List<CustomerDataResponseDTO>>> getCustomerDataByClientId(
            @PathVariable Long clientId) {
        log.info("User fetching customer data for clientId: {}", clientId);
        return ResponseEntity.ok(
                ApiResponseDTO.<List<CustomerDataResponseDTO>>builder()
                        .status("SUCCESS")
                        .message("Customer data fetched successfully")
                        .data(customerDataService.getCustomerDataByClientId(clientId))
                        .build()
        );
    }

    @GetMapping("/import-stats/{clientId}")
    @Operation(summary = "Get import stats (logs) by client ID", description = "Access Level: Protected [Required Role: ADMIN, CLIENT]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import stats fetched successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or Client role required")
    })
    public ResponseEntity<ApiResponseDTO<com.whatsupmarketplacebackend.dto.ImportLogResponseDTO>> getImportStatsByClientId(
            @PathVariable Long clientId) {
        log.info("User fetching import stats for clientId: {}", clientId);
        com.whatsupmarketplacebackend.dto.ImportLogResponseDTO stat = customerDataService.getLatestImportStatByClientId(clientId);
        
        return ResponseEntity.ok(
                ApiResponseDTO.<com.whatsupmarketplacebackend.dto.ImportLogResponseDTO>builder()
                        .status("SUCCESS")
                        .message("Import stats fetched successfully")
                        .data(stat)
                        .build()
        );
    }
}
