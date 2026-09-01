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
public class ConsultationPaymentInitiateDTO {

    private String orderId;
    private BigDecimal amount;
    private Long consultationRequestId;
    private Long customerId;
    private Long lawyerId;
    private String lawyerName;
    private String currency;
    private String message;
}
