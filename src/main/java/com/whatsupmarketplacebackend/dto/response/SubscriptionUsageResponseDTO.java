package com.whatsupmarketplacebackend.dto.response;

import lombok.Data;

@Data
public class SubscriptionUsageResponseDTO {
    private Long clientId;
    private String planName;
    private Integer totalMessagesAllowed;
    private Integer remainingMessages;
    private Integer messagesUsed;
    private Integer totalCampaignsAllowed;
    private Integer campaignsUsed;
    private Integer remainingCampaigns;
    private String daysRemaining;
}
