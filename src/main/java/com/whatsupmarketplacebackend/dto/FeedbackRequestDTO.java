package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.enums.BusinessCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record FeedbackRequestDTO(
    Long clientId,

    @NotBlank(message = "Client name is required")
    @Size(max = 100, message = "Client name must not exceed 100 characters")
    String clientName,

    @Size(max = 100, message = "Company name must not exceed 100 characters")
    String companyName,

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    String designation,

    @NotNull(message = "Service category is required")
    BusinessCategory serviceName,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    Integer rating,

    @NotBlank(message = "Feedback comment is required")
    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    String comment
) {}
