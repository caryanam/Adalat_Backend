package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AdminRejectRequestDTO {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
