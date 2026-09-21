package com.adalat.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawyerTransactionDTO {
    private String id;
    private Long requestId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String amount;
    private BigDecimal amountNum;
    private String date;
    private String rawDate;
    private String duration;
    private String status;
    private String paymentType;
    private String upiId;
    private String category;
}
