package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendLegalMessageRequestDTO {

    @NotBlank(message = "Message cannot be empty")
    private String message;
}
