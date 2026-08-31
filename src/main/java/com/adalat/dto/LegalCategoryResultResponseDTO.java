package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalCategoryResultResponseDTO {

    private Long sessionId;
    private LegalCategory likelyLegalCategory;
    private String categoryDisplayName;
    private PracticeArea relevantPracticeArea;
    private String practiceAreaDisplayName;
    private String summary;
    private Double confidence;
    private String disclaimer;
}
