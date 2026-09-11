package com.adalat.service.ai.engine;

import com.adalat.enums.LegalCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionEngineTest {

    private CompletionEngine engine;
    private FactMergeService factMergeService;

    @BeforeEach
    void setUp() {
        factMergeService = new FactMergeService();
        engine = new CompletionEngine(factMergeService);
    }

    @Test
    void testNegativeKnownFactsCountAsSatisfied() {
        // "none" must count as a satisfied dimension, not missing
        Map<String, Object> facts = new HashMap<>();
        facts.put("incident", "vehicle theft");
        facts.put("incident_location", "Kharadi");
        facts.put("police_status", "none");  // Known: no police complaint
        facts.put("injury_status", "none");  // Known: no injuries
        facts.put("evidence_status", "RC and keys available");
        facts.put("desired_outcome", "recover vehicle");
        facts.put("urgency", "NORMAL");

        List<String> missing = engine.getMissingCriticalFacts(LegalCategory.CRIMINAL, facts, "Pune", "Maharashtra");
        // police_status and injury_status should NOT be in the missing list
        assertFalse(missing.contains("police_status"), "police_status='none' should be treated as known");
        assertFalse(missing.contains("injury_status"), "injury_status='none' should be treated as known");
    }

    @Test
    void testSufficientCaseCompletesEarly() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("incident", "vehicle theft");
        facts.put("incident_location", "Kharadi, Pune");
        facts.put("police_status", "none");
        facts.put("injury_status", "none");
        facts.put("evidence_status", "RC and keys available");
        facts.put("desired_outcome", "recover vehicle");
        facts.put("urgency", "HIGH");

        boolean complete = engine.isIntakeComplete(LegalCategory.CRIMINAL, facts, "Pune", "Maharashtra", 4, false);
        assertTrue(complete, "Backend should declare complete when 70%+ dimensions satisfied with core problem known");
    }

    @Test
    void testMaxQuestionCapWorks() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("incident", "theft");

        boolean complete = engine.isIntakeComplete(LegalCategory.CRIMINAL, facts, null, null, 10, false);
        assertTrue(complete, "Max question cap should force completion");
    }

    @Test
    void testMissingCoreProblemBlocksEarlyCompletion() {
        Map<String, Object> facts = new HashMap<>();
        // No core_problem or incident
        facts.put("jurisdiction_city", "Pune");
        facts.put("evidence_status", "some docs");

        boolean complete = engine.isIntakeComplete(LegalCategory.CRIMINAL, facts, "Pune", null, 3, false);
        assertFalse(complete, "Cannot complete without core problem");
    }
}
