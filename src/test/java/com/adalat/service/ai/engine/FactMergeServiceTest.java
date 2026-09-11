package com.adalat.service.ai.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FactMergeServiceTest {

    private FactMergeService service;

    @BeforeEach
    void setUp() {
        service = new FactMergeService();
    }

    @Test
    void testNoneIsPreserved() {
        // "none" must be treated as a meaningful known-negative value
        assertTrue(service.isMeaningfulValue("none"));
        assertTrue(service.isMeaningfulValue("None"));
    }

    @Test
    void testFalseIsPreserved() {
        // Boolean false is a meaningful value
        assertTrue(service.isMeaningfulValue(false));
        assertTrue(service.isMeaningfulValue(Boolean.FALSE));
    }

    @Test
    void testUnknownIsNotMeaningful() {
        assertFalse(service.isMeaningfulValue("unknown"));
        assertFalse(service.isMeaningfulValue("not_provided"));
        assertFalse(service.isMeaningfulValue("null"));
        assertFalse(service.isMeaningfulValue(null));
        assertFalse(service.isMeaningfulValue(""));
    }

    @Test
    void testNullDoesNotOverwriteExisting() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("firFiled", false);

        Map<String, Object> newFacts = new HashMap<>();
        newFacts.put("firFiled", null);

        Map<String, Object> merged = service.mergeFacts(existing, newFacts, "some message");
        assertEquals(false, merged.get("firFiled"));
    }

    @Test
    void testBlankStringDoesNotOverwrite() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("location", "Pune");

        Map<String, Object> newFacts = new HashMap<>();
        newFacts.put("location", "");

        Map<String, Object> merged = service.mergeFacts(existing, newFacts, "");
        assertEquals("Pune", merged.get("location"));
    }

    @Test
    void testMultipleFactsMerge() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("incident", "vehicle theft");

        Map<String, Object> newFacts = new HashMap<>();
        newFacts.put("cctvAvailable", false);
        newFacts.put("rcAvailable", true);
        newFacts.put("keysAvailable", true);

        Map<String, Object> merged = service.mergeFacts(existing, newFacts, "cctv nahi pan rc ani chavi ahe");
        assertEquals("vehicle theft", merged.get("incident"));
        assertEquals(false, merged.get("cctvAvailable"));
        assertEquals(true, merged.get("rcAvailable"));
        assertEquals(true, merged.get("keysAvailable"));
    }

    @Test
    void testExplicitCorrectionOverrides() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("cctvAvailable", true);

        Map<String, Object> newFacts = new HashMap<>();
        newFacts.put("cctvAvailable", false);

        Map<String, Object> merged = service.mergeFacts(existing, newFacts, "nahi mi chukicha sangitla hota cctv nahiye");
        assertEquals(false, merged.get("cctvAvailable"));
    }

    @Test
    void testNoneValueStoredAndNotDropped() {
        Map<String, Object> existing = new HashMap<>();

        Map<String, Object> newFacts = new HashMap<>();
        newFacts.put("injury_status", "none");
        newFacts.put("prior_police_complaint", "none");

        Map<String, Object> merged = service.mergeFacts(existing, newFacts, "no injuries and no police complaint");
        assertEquals("none", merged.get("injury_status"));
        assertEquals("none", merged.get("prior_police_complaint"));
    }
}
