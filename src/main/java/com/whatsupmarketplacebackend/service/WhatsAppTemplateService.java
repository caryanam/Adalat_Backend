package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.WhatsAppTemplateDTO;
import java.util.List;

public interface WhatsAppTemplateService {
    List<WhatsAppTemplateDTO> getAllTemplates();
    WhatsAppTemplateDTO createTemplate(WhatsAppTemplateDTO request);
}
