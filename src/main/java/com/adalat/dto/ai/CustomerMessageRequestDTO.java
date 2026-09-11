package com.adalat.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMessageRequestDTO {
    @NotBlank(message = "Message content cannot be blank")
    private String message;

    private String clientMessageId;
}
