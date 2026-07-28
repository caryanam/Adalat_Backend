package com.whatsupmarketplacebackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseSubscriptionRequestDTO {

    @NotNull(message = "Plan ID is required")
    private Long planId;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

}
