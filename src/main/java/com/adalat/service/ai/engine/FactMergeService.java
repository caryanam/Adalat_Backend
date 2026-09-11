package com.adalat.service.ai.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FactMergeService {

    private static final Set<String> NON_MEANINGFUL_STRINGS = Set.of(
            "unknown", "not_provided", "not_confirmed", "null", "n/a", "na", "undefined"
    );

    /**
     * Checks if a fact value is considered meaningful.
     * Rejects null, empty strings, and non-informative placeholders like "unknown".
     */
    public boolean isMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            String trimmed = str.trim().toLowerCase();
            return !trimmed.isEmpty() && !NON_MEANINGFUL_STRINGS.contains(trimmed);
        }
        return true;
    }

    /**
     * Merges newly extracted facts into existing collected facts following strict rules:
     * 1. UNKNOWN/empty + known value -> store known value
     * 2. known value + same value -> keep existing value
     * 3. known value + explicit corrected value from user -> update value
     * 4. known value + conflicting uncertain AI extraction -> do NOT silently overwrite
     * 5. The customer’s explicit latest statement has priority over older inferred values.
     */
    public Map<String, Object> mergeFacts(Map<String, Object> existingFacts,
                                          Map<String, Object> newFacts,
                                          String latestCustomerMessage) {
        Map<String, Object> merged = existingFacts != null ? new HashMap<>(existingFacts) : new HashMap<>();
        String userMsgLower = latestCustomerMessage != null ? latestCustomerMessage.toLowerCase() : "";

        if (newFacts != null && !newFacts.isEmpty()) {
            for (Map.Entry<String, Object> entry : newFacts.entrySet()) {
                String key = entry.getKey();
                Object newVal = entry.getValue();

                if (!isMeaningfulValue(newVal)) {
                    continue;
                }

                Object existingVal = merged.get(key);

                if (!isMeaningfulValue(existingVal)) {
                    // Rule 1: Unknown/empty + known value -> store known value
                    merged.put(key, newVal);
                    log.info("Fact stored [{}]: {}", key, newVal);
                } else if (existingVal.toString().trim().equalsIgnoreCase(newVal.toString().trim())) {
                    // Rule 2: Same value -> keep existing value
                    log.debug("Fact unchanged [{}]: {}", key, existingVal);
                } else {
                    // Value conflict: Determine whether user explicitly corrected it
                    boolean isCorrection = isExplicitUserCorrection(newVal, userMsgLower);
                    if (isCorrection) {
                        // Rule 3: User explicit correction has priority -> update value
                        merged.put(key, newVal);
                        log.info("Fact corrected by user [{}]: '{}' -> '{}'", key, existingVal, newVal);
                    } else {
                        // Rule 4: Conflicting uncertain AI extraction -> do not silently overwrite
                        log.warn("Fact conflict for [{}]. Keeping existing '{}', rejecting AI extraction '{}'",
                                key, existingVal, newVal);
                    }
                }
            }
        }

        // Keyword fallback extraction for explicit customer facts when AI extraction misses them
        if (userMsgLower.contains("fir copy") || userMsgLower.contains("fir chi copy") || (userMsgLower.contains("fir") && userMsgLower.contains("copy"))) {
            if (!isMeaningfulValue(merged.get("fir_copy_available")) && !isMeaningfulValue(merged.get("fir_copy"))) {
                merged.put("fir_copy_available", true);
                merged.put("fir_filed", true);
                log.info("FactMergeService: extracted fir_copy_available=true from customer message keywords");
            }
        }
        if (userMsgLower.contains("accident") || userMsgLower.contains("accident kela")) {
            if (!isMeaningfulValue(merged.get("stolen_vehicle_accident")) && !isMeaningfulValue(merged.get("accident"))) {
                merged.put("stolen_vehicle_accident", true);
                log.info("FactMergeService: extracted stolen_vehicle_accident=true from customer message keywords");
            }
        }
        if (userMsgLower.contains("whatsapp") || userMsgLower.contains("whatapp")) {
            if (!isMeaningfulValue(merged.get("whatsapp_evidence")) && !isMeaningfulValue(merged.get("chat_evidence"))) {
                merged.put("whatsapp_evidence", true);
                log.info("FactMergeService: extracted whatsapp_evidence=true from customer message keywords");
            }
        }

        return merged;
    }

    /**
     * Determines if the new value was explicitly indicated or corrected in the user's latest message.
     */
    private boolean isExplicitUserCorrection(Object newVal, String userMsgLower) {
        String newValStr = newVal.toString().trim().toLowerCase();
        
        // If the user's message directly contains the new value
        if (userMsgLower.contains(newValStr)) {
            return true;
        }

        // Common correction indicators in English, Hindi, and Marathi
        boolean hasCorrectionKeyword = userMsgLower.contains("actually") ||
                userMsgLower.contains("sorry") ||
                userMsgLower.contains("mistake") ||
                userMsgLower.contains("correction") ||
                userMsgLower.contains("change") ||
                userMsgLower.contains("instead") ||
                userMsgLower.contains("nahi") ||
                userMsgLower.contains("not ") ||
                userMsgLower.contains("chuki") ||
                userMsgLower.contains("galti");

        if (hasCorrectionKeyword) {
            return true;
        }

        // Check token overlap if newVal is multi-word
        String[] tokens = newValStr.split("\\s+");
        int matches = 0;
        for (String t : tokens) {
            if (t.length() > 2 && userMsgLower.contains(t)) {
                matches++;
            }
        }
        return matches > 0 && matches >= (tokens.length / 2);
    }
}
