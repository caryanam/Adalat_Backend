package com.whatsupmarketplacebackend.dto.response;

import com.whatsupmarketplacebackend.enums.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentHistoryResponseDTO {
    private Long id;
    private Long clientSubscriptionId;
    private Double amount;
    private String paymentMethod;
    private PaymentStatus status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String remarks;
    private LocalDateTime createdAt;
    private String planName;
}
