package com.whatsupmarketplacebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppTemplateVariableDTO {
    private String name;
    private String label;
    private String placeholder;
    private String defaultValue;
}
