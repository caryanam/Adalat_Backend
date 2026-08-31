package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalQuestionResponseDTO {

    private Long id;
    private Long sessionId;
    private Integer questionNumber;
    private String questionText;
    private LegalCategory category;
    private String categoryDisplayName;
    private LocalDateTime createdAt;
}
