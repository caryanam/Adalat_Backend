package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.enums.BusinessCategory;
import com.whatsupmarketplacebackend.enums.FeedbackStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record FeedbackResponseDTO(
    Long id,
    Long clientId,
    String clientName,
    String companyName,
    String designation,
    BusinessCategory serviceName,
    Integer rating,
    String comment,
    FeedbackStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
