package com.adalat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalAnswerRequestDTO {

    private Long questionId;

    @NotBlank(message = "Answer text cannot be empty")
    private String answerText;
}
