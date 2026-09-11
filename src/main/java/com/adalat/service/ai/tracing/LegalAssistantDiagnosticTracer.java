package com.adalat.service.ai.tracing;

import com.adalat.dto.ai.TurnDiagnosticTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LegalAssistantDiagnosticTracer {

    public void logTrace(TurnDiagnosticTrace trace) {
        if (trace == null) return;

        log.info("==================================================");
        log.info("DIAGNOSTIC TURN TRACE [Session ID: {}]", trace.getSessionId());
        log.info("==================================================");
        log.info("Detected Language:        {}", trace.getDetectedLanguage());
        log.info("Detected Intent:          {}", trace.getDetectedIntent());
        log.info("Session State:            {}", trace.getSessionState());
        log.info("Facts Before Merge:       {}", sanitize(trace.getStructuredFactsBeforeMerge()));
        log.info("AI New Facts:             {}", sanitize(trace.getAiNewFacts()));
        log.info("Facts After Merge:        {}", sanitize(trace.getStructuredFactsAfterMerge()));
        log.info("Missing Critical Facts:   {}", trace.getMissingCriticalFacts());
        log.info("AI NextQuestionFactKey:   {}", trace.getAiNextQuestionFactKey());
        log.info("AI NextQuestion:          {}", trace.getAiNextQuestion());
        log.info("AntiLoop Rejected:        {}", trace.isAntiLoopRejected());
        log.info("Regeneration Attempted:   {}", trace.isRegenerationAttempted());
        log.info("Deterministic Fallback:   {}", trace.isDeterministicFallbackUsed());
        log.info("Final Persisted Question: {}", trace.getFinalPersistedQuestion());
        log.info("Final API Assistant Msg:  {}", trace.getFinalApiAssistantMessage());
        log.info("==================================================");
    }

    private Object sanitize(Object facts) {
        if (facts == null) return "{}";
        // String representation of facts is safe (no passwords or credentials stored in facts)
        return facts;
    }
}
