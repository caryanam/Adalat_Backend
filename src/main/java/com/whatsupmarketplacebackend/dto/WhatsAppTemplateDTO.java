package com.whatsupmarketplacebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppTemplateDTO {
    private Long id;
    private String name;
    private String category;
    private String headerType;
    private String bodyTemplate;
    private String defaultHeaderUrl;
    private List<WhatsAppTemplateVariableDTO> variables;
}
