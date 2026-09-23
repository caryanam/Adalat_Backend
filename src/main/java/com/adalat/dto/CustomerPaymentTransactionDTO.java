package com.adalat.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPaymentTransactionDTO {
    private String id;
    private String orderId;
    private String gatewayPaymentId;
    private Long consultationRequestId;
    private String paymentType; // REGISTRATION or CONSULTATION_FEE
    private String serviceDescription;
    private String serviceSubDescription;
    private Long lawyerId;
    private String lawyerName;
    private String lawyerProfileImageUrl;
    private String category;
    private String amount; // e.g. "₹116.82"
    private BigDecimal amountNum; // 116.82
    private String baseAmount; // "99.00"
    private String gstAmount; // "17.82"
    private String paymentMethod; // "UPI Direct (Auto-Settled)"
    private String status; // "PAID", "PENDING", "FAILED"
    private String date; // "22 Sep 2026, 05:41 PM"
    private String rawDate;
}
