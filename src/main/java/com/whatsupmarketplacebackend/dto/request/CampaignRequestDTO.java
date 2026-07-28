package com.whatsupmarketplacebackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CampaignRequestDTO {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotBlank(message = "Campaign name is required")
    private String campaignName;

    private Long templateId;

    private String headerImageUrl;

    private Map<String, String> templateValues;
}
