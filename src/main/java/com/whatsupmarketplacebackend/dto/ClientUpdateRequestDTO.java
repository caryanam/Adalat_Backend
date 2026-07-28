package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.enums.BusinessCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ClientUpdateRequestDTO(

        @NotBlank(message = "Owner name is required")
        String ownerName,

        @NotBlank(message = "Company name is required")
        String companyName,

        @NotNull(message = "Business category is required")
        BusinessCategory category,

        @Pattern(regexp = "^[6-9]\\d{9}$")
        String phoneNumber,

        @Pattern(regexp = "^[6-9]\\d{9}$")
        String whatsappNumber,

        @NotBlank(message = "Email is required")
        @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Enter a valid email address")
        String email
) {
}
