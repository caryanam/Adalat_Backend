package com.whatsupmarketplacebackend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCreateRequestDTO {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotBlank(message = "Campaign name is required")
    private String campaignName;

    @NotNull(message = "Template ID is required")
    private Long templateId;

    private String headerImageUrl;

    @Valid
    private List<VariableMappingDTO> variableMappings;

    @Min(value = 1, message = "Message limit must be at least 1")
    private Integer messageLimit;

    @Min(value = 1, message = "Delay must be at least 1 second")
    @Max(value = 300, message = "Delay cannot exceed 300 seconds")
    private Integer delayBetweenMessages = 10;
}
