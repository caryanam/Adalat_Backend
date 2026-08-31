package com.adalat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentInitiateResponseDTO {

    private String orderId;
    private BigDecimal amount;
    private Long customerId;
    private String currency;
    private String message;
}
