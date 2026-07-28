package com.whatsupmarketplacebackend.dto.request;

import com.whatsupmarketplacebackend.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentApprovalRequestDTO {
    
    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    private PaymentStatus status;
    
    @NotBlank(message = "Remarks are required for approval/rejection")
    private String remarks;
}
