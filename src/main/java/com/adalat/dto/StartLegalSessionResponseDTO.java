package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.LegalSessionStatus;
import com.adalat.enums.PracticeArea;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StartLegalSessionResponseDTO {

    private Long sessionId;
    private LegalCategory selectedCategory;
    private String categoryDisplayName;
    private LegalCategory aiDetectedCategory;
    private PracticeArea practiceArea;
    private LegalSessionStatus status;
    private Integer currentQuestionNumber;
    private Integer totalQuestions;
    private LegalChatMessageResponseDTO initialMessage;
    private LegalQuestionResponseDTO firstQuestion;
    private LocalDateTime createdAt;
}
