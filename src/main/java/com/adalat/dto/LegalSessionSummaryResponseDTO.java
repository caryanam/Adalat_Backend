package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.LegalSessionStatus;
import com.adalat.enums.PracticeArea;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalSessionSummaryResponseDTO {

    private Long sessionId;
    private LegalCategory likelyLegalCategory;
    private String categoryDisplayName;
    private PracticeArea relevantPracticeArea;
    private String practiceAreaDisplayName;
    private String summary;
    private LegalSessionStatus status;
    private Integer totalQuestionsAnswered;
    private Integer documentsUploadedCount;
    private List<LawyerSuggestionResponseDTO> suggestedLawyers;
    private String disclaimer;
}
