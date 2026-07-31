package com.whatsupmarketplacebackend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateResponseDTO {

    private Long id;
    private String templateName;
    private String category;
    private String language;
    private String status;
    private String headerType;
    private String headerText;
    private String headerVariables;
    private String body;
    private String footer;
    private String buttons;
    private Integer variableCount;
    private String metaTemplateId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
