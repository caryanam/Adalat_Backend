package com.adalat.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIntakeRequestDTO {
    private String systemRules;
    private CurrentState currentState;
    private Set<String> askedFactKeys;
    private List<String> askedQuestions;
    private List<String> missingCriticalFacts;
    private String latestCustomerMessage;
    private String previousQuestionFactKey;
    private String previousQuestionText;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentState {
        private String primaryCategory;
        private List<String> secondaryCategories;
        private JurisdictionDTO jurisdiction;
        private Map<String, Object> collectedFacts;
        private String urgency;
    }
}
