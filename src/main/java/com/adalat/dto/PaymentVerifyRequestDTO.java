package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentVerifyRequestDTO {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotBlank(message = "Order ID is required")
    private String orderId;

    // Optional: gateway-provided payment ID and signature for production Razorpay verification
    private String gatewayPaymentId;
    private String gatewaySignature;
}
