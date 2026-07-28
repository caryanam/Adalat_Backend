package com.whatsupmarketplacebackend.dto.response;

import lombok.Data;

@Data
public class AdminAnalyticsResponseDTO {
    private Long totalActiveSubscriptions;
    private Long totalExpiredSubscriptions;
    private Double totalRevenue;
    private Long pendingPayments;
    private Long totalCampaignsRun;
    private Long totalMessagesSent;
}
