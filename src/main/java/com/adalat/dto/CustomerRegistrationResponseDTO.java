package com.adalat.dto;

import com.adalat.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerRegistrationResponseDTO {

    private Long customerId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private PaymentStatus paymentStatus;
    private String message;
}
