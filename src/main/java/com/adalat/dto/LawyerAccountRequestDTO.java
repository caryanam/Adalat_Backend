package com.adalat.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerAccountRequestDTO {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Full name must be at least 3 characters")
    @Pattern(
        regexp = "^(?=.*[a-zA-Z].*[a-zA-Z].*[a-zA-Z])[a-zA-Z][a-zA-Z\\s.'-]*[a-zA-Z.]+$",
        message = "Full Name must contain at least 3 alphabetic characters and cannot be single letters or dots (e.g. Adv. Rajesh Verma)"
    )
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;
}
