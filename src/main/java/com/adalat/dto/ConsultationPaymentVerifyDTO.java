package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultationPaymentVerifyDTO {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    private String gatewayPaymentId;
    private String gatewaySignature;
}
