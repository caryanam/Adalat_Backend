package com.whatsupmarketplacebackend.dto.response;

import com.whatsupmarketplacebackend.enums.CampaignStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignDetailResponseDTO {

    private Long id;
    private String campaignName;
    private Long clientId;
    private String clientCompanyName;
    private Long templateId;
    private String templateName;
    private String headerImageUrl;
    private Integer messageLimit;
    private Integer delayBetweenMessages;
    private CampaignStatus campaignStatus;

    // Aggregate Stats
    private Integer totalRecipients;
    private Integer messagesSent;
    private Integer messagesDelivered;
    private Integer messagesRead;
    private Integer messagesFailed;
    private Integer messagesRetrying;
    private Double completionPercentage;

    // Variable Mappings
    private List<VariableMappingResponseDTO> variableMappings;

    // Timestamps
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariableMappingResponseDTO {
        private Integer variableIndex;
        private String variableType;
        private String fieldName;
        private String staticValue;
    }
}
