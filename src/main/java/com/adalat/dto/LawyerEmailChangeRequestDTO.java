package com.adalat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerEmailChangeRequestDTO {

    @NotBlank(message = "New email address is required")
    @Email(message = "Please provide a valid email address")
    private String newEmail;
}
