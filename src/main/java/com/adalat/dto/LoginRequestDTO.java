package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "Email or phone number is required")
    private String identifier; // This can be email or phone number

    @NotBlank(message = "Password is required")
    private String password;

}
