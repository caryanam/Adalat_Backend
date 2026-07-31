package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.response.TemplateResponseDTO;

import java.util.List;

public interface WhatsAppTemplateService {

    int syncTemplatesFromMeta();

    List<TemplateResponseDTO> getAllTemplates();

    TemplateResponseDTO getTemplateById(Long id);

    void deleteTemplate(Long id);
}
