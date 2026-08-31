package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerUpiRequestDTO {

    @NotBlank(message = "UPI ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$",
        message = "UPI ID must be in format: username@bankname (e.g. 9876543210@upi)"
    )
    private String upiId;
}
