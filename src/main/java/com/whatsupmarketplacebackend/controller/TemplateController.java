package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.response.TemplateResponseDTO;
import com.whatsupmarketplacebackend.service.WhatsAppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Template management REST API.
 * Templates are synced from Meta WhatsApp Business API — not hardcoded.
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final WhatsAppTemplateService templateService;

    /**
     * Sync all templates from Meta WhatsApp Business API.
     * Performs upsert: creates new templates and updates existing ones.
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponseDTO<Object>> syncTemplates() {
        int synced = templateService.syncTemplatesFromMeta();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Successfully synced " + synced + " templates from Meta API.", synced));
    }

    /**
     * Refresh templates (alias for sync).
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDTO<Object>> refreshTemplates() {
        int synced = templateService.syncTemplatesFromMeta();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Successfully refreshed " + synced + " templates from Meta API.", synced));
    }

    /**
     * Get all templates.
     */
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<TemplateResponseDTO>>> getAllTemplates() {
        List<TemplateResponseDTO> templates = templateService.getAllTemplates();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Templates fetched successfully.", templates));
    }

    /**
     * Get template by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<TemplateResponseDTO>> getTemplateById(@PathVariable Long id) {
        TemplateResponseDTO template = templateService.getTemplateById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Template fetched successfully.", template));
    }

    /**
     * Delete template by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<Object>> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Template deleted successfully.", null));
    }
}
