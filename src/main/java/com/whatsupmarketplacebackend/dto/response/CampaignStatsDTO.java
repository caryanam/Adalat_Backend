package com.whatsupmarketplacebackend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignStatsDTO {

    private Long campaignId;
    private String campaignName;
    private String campaignStatus;

    private Integer totalRecipients;
    private Integer queued;
    private Integer processing;
    private Integer sent;
    private Integer delivered;
    private Integer read;
    private Integer failed;
    private Integer retrying;
    private Integer cancelled;

    private Double completionPercentage;
    private Double deliveryRate;
    private Double readRate;
}
