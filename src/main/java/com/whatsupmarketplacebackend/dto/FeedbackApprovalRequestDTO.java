package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record FeedbackApprovalRequestDTO(
    @NotNull(message = "Feedback ID is required")
    Long feedbackId,

    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    FeedbackStatus status
) {}
