package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequestDTO {

    @NotBlank(message = "Registered email or mobile number is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
