package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.LegalSessionStatus;
import com.adalat.enums.PracticeArea;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalSessionDetailResponseDTO {

    private Long sessionId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerMobileNumber;
    private LegalCategory selectedCategory;
    private LegalCategory aiDetectedCategory;
    private String categoryDisplayName;
    private PracticeArea practiceArea;
    private LegalSessionStatus status;
    private Integer currentQuestionNumber;
    private Integer totalQuestions;
    private String initialProblemDescription;
    private String summary;
    private List<LegalChatMessageResponseDTO> messages;
    private List<LegalDocumentResponseDTO> documents;
    private List<LawyerSuggestionResponseDTO> suggestedLawyers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
