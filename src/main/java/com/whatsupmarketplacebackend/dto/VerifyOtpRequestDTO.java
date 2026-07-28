package com.whatsupmarketplacebackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequestDTO(

        @NotBlank(message = "Email is required")
        @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Enter a valid email address")
        String email,

        @NotBlank(message = "OTP is required")
        String otp
) {
}
