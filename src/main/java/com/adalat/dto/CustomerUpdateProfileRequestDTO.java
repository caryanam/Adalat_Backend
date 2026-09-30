package com.adalat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateProfileRequestDTO {

    @NotBlank(message = "Full name is required")
    @jakarta.validation.constraints.Size(min = 3, max = 100, message = "Full name must be at least 3 characters")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[a-zA-Z].*[a-zA-Z].*[a-zA-Z])[a-zA-Z][a-zA-Z\\s.'-]*[a-zA-Z.]+$",
        message = "Full Name must contain at least 3 alphabetic characters and cannot be single letters or dots (e.g. John Doe)"
    )
    private String fullName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian mobile number")
    private String mobileNumber;
}
