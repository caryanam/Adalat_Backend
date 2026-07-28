package com.whatsupmarketplacebackend.dto.response;

import com.whatsupmarketplacebackend.enums.CampaignStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CampaignResponseDTO {
    private Long id;
    private Long clientId;
    private Long subscriptionId;
    private String campaignName;
    private Long templateId;
    private String headerImageUrl;
    private CampaignStatus campaignStatus;
    private Integer messagesSent;
    private Integer remainingMessages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
