package com.adalat.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TurnDiagnosticTrace {
    private Long sessionId;
    private String detectedLanguage;
    private String detectedIntent;
    private String sessionState;
    private Map<String, Object> structuredFactsBeforeMerge;
    private Map<String, Object> aiNewFacts;
    private Map<String, Object> structuredFactsAfterMerge;
    private List<String> missingCriticalFacts;
    private String aiNextQuestionFactKey;
    private String aiNextQuestion;
    private boolean antiLoopRejected;
    private boolean regenerationAttempted;
    private boolean deterministicFallbackUsed;
    private String finalPersistedQuestion;
    private String finalApiAssistantMessage;
}
