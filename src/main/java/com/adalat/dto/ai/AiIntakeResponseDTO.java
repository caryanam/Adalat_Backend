package com.adalat.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiIntakeResponseDTO {
    private String assistantMessage;
    private String primaryCategory;
    private List<String> secondaryCategories;
    private JurisdictionDTO jurisdiction;
    private Map<String, Object> newFacts;
    private String urgency;
    private List<String> missingCriticalFacts;
    private String nextQuestionFactKey;
    private String nextQuestion;
    private boolean intakeComplete;
    private boolean clarificationNeeded;
    private String customerSummary;
    private String lawyerSummary;
}
