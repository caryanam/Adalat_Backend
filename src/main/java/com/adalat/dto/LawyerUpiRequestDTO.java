package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerUpiRequestDTO {

    @NotBlank(message = "UPI ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$",
        message = "UPI ID must be in valid format: username@bankhandle (e.g. name@upi, 9876543210@paytm)"
    )
    private String upiId;
}
