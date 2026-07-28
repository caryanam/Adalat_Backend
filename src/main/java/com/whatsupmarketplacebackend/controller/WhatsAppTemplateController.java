package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.WhatsAppTemplateDTO;
import com.whatsupmarketplacebackend.service.WhatsAppTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/whatsapp-templates")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Templates", description = "Endpoints for fetching WhatsApp Cloud API approved marketing templates")
public class WhatsAppTemplateController {

    private final WhatsAppTemplateService whatsappTemplateService;

    @GetMapping
    @Operation(summary = "Get all predefined WhatsApp marketing templates", description = "Access Level: Protected [Required Role: ADMIN, CLIENT]")
    public ResponseEntity<ApiResponseDTO<List<WhatsAppTemplateDTO>>> getAllTemplates() {
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "WhatsApp templates fetched successfully", whatsappTemplateService.getAllTemplates()));
    }

    @PostMapping
    @Operation(summary = "Create a new WhatsApp template (ADMIN Only)", description = "Access Level: Protected [Required Role: ADMIN]")
    public ResponseEntity<ApiResponseDTO<WhatsAppTemplateDTO>> createTemplate(@Valid @RequestBody WhatsAppTemplateDTO request) {
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "WhatsApp template created successfully", whatsappTemplateService.createTemplate(request)));
    }
}
