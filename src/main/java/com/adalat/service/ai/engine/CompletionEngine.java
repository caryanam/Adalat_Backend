package com.adalat.service.ai.engine;

import com.adalat.enums.LegalCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletionEngine {

    public static final int MAX_FOLLOW_UP_QUESTIONS = 10;

    private final FactMergeService factMergeService;

    // Required dimensions for each category mapped to accepted alias keys in collectedFacts
    private static final Map<LegalCategory, List<List<String>>> REQUIRED_DIMENSIONS = new EnumMap<>(LegalCategory.class);

    static {
        // TENANCY
        REQUIRED_DIMENSIONS.put(LegalCategory.TENANCY, List.of(
                List.of("core_problem", "incident", "dispute_details"),
                List.of("agreement_status", "agreement_type", "lease_status"),
                List.of("eviction_or_notice_details", "eviction_notice", "eviction_deadline", "threat_evidence"),
                List.of("possession_status", "rent_payment_status", "deposit_status"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "documents", "receipts", "screenshots", "whatsapp"),
                List.of("desired_outcome", "remedy_sought", "goal"),
                List.of("jurisdiction", "jurisdiction_city", "jurisdiction_state", "city", "location")
        ));

        // EMPLOYMENT_SALARY & EMPLOYMENT
        List<List<String>> employmentSalaryDims = List.of(
                List.of("core_problem", "incident", "issue"),
                List.of("salary_pending_period", "pending_months", "unpaid_period", "pending_month_count"),
                List.of("employment_status", "job_status", "designation"),
                List.of("employer_response", "hr_response", "management_action"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "salary_evidence", "salary_slips", "offer_letter", "bank_statements", "documents", "screenshots", "whatsapp"),
                List.of("desired_outcome", "goal", "remedy_sought"),
                List.of("jurisdiction", "jurisdiction_city", "jurisdiction_state", "city", "location")
        );
        REQUIRED_DIMENSIONS.put(LegalCategory.EMPLOYMENT_SALARY, employmentSalaryDims);
        REQUIRED_DIMENSIONS.put(LegalCategory.EMPLOYMENT, employmentSalaryDims);

        // CRIMINAL
        REQUIRED_DIMENSIONS.put(LegalCategory.CRIMINAL, List.of(
                List.of("incident", "core_problem", "offence_type"),
                List.of("incident_location", "jurisdiction_city", "jurisdiction_state", "city", "location"),
                List.of("police_status", "police_complaint_status", "fir_status", "station_visited", "police_involvement", "complaint_filed", "fir_filed"),
                List.of("injury_status", "physical_harm", "medical_status"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "medical_evidence", "witnesses", "cctv_evidence", "video_evidence", "documents", "screenshots", "whatsapp", "fir_copy", "fir_copy_available", "theft_evidence", "fir_evidence"),
                List.of("desired_outcome", "goal", "safety_concern"),
                List.of("urgency", "immediate_threat", "safety_threat")
        ));

        // CONSUMER
        REQUIRED_DIMENSIONS.put(LegalCategory.CONSUMER, List.of(
                List.of("product_or_service", "core_problem", "item_purchased"),
                List.of("merchant_name", "company_name", "seller_response", "merchant_response"),
                List.of("payment_or_amount", "purchase_amount", "purchase_evidence", "invoice_receipt"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "bill_copy", "email_complaint", "warranty_card", "documents", "screenshots", "whatsapp"),
                List.of("desired_outcome", "refund_sought", "replacement_sought")
        ));

        // PROPERTY
        REQUIRED_DIMENSIONS.put(LegalCategory.PROPERTY, List.of(
                List.of("core_problem", "dispute_nature", "property_type"),
                List.of("parties_involved", "opposing_party", "family_or_third_party"),
                List.of("possession_status", "current_occupant"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "title_papers", "registry_status", "mutation_status", "documents", "screenshots", "whatsapp"),
                List.of("desired_outcome", "possession_recovery", "injunction"),
                List.of("jurisdiction", "jurisdiction_city", "jurisdiction_state", "city")
        ));

        // FAMILY
        REQUIRED_DIMENSIONS.put(LegalCategory.FAMILY, List.of(
                List.of("core_problem", "dispute_nature", "divorce_dispute", "child_custody", "child_access", "alimony", "maintenance", "separation", "domestic_dispute", "child_involvement", "alimony_demand", "divorce_status"),
                List.of("marriage_status", "marriage_details", "marriage_date", "relationship_timeline"),
                List.of("legal_action_status", "previous_action", "police_involvement", "police_status", "fir_status", "court_case_status", "evidence_status"),
                List.of("desired_outcome", "remedy_sought", "divorce_mutual", "reconciliation"),
                List.of("jurisdiction", "jurisdiction_city", "jurisdiction_state", "city")
        ));

        // CIVIL
        REQUIRED_DIMENSIONS.put(LegalCategory.CIVIL, List.of(
                List.of("core_problem", "incident", "dispute_details"),
                List.of("parties_involved", "opposing_party"),
                List.of("monetary_claim", "contract_value", "amount_disputed"),
                List.of("evidence_status", "evidence", "proof", "whatsapp_evidence", "chat_evidence", "agreement_copy", "notices_exchanged", "documents", "screenshots", "whatsapp"),
                List.of("desired_outcome", "recovery", "specific_performance")
        ));
    }

    /**
     * Fallback standard required dimensions if category is unmapped or general.
     */
    private static final List<List<String>> DEFAULT_DIMENSIONS = List.of(
            List.of("core_problem", "incident", "issue"),
            List.of("parties_involved", "opposing_party"),
            List.of("timeline_or_incident_date", "dates"),
            List.of("evidence_status", "documents"),
            List.of("desired_outcome", "goal")
    );

    /**
     * Determines whether the intake has collected enough category-aware facts
     * for an initial lawyer assessment. Spring Boot is the authoritative decision maker.
     */
    public boolean isIntakeComplete(LegalCategory category,
                                    Map<String, Object> facts,
                                    String city,
                                    String state,
                                    int questionCount,
                                    boolean aiIntakeComplete) {

        // Safety guard: max questions reached -> must stop questioning
        if (questionCount >= MAX_FOLLOW_UP_QUESTIONS) {
            log.info("Max follow-up questions ({}) reached. Forcing intake completion.", MAX_FOLLOW_UP_QUESTIONS);
            return true;
        }

        List<String> missingCritical = getMissingCriticalFacts(category, facts, city, state);

        // Case A: All required dimensions for the category are satisfied -> Complete!
        // (Even if LLM said false, backend decides it's complete)
        if (missingCritical.isEmpty()) {
            log.info("All required dimensions satisfied for category {}. Backend declares complete.", category);
            return true;
        }

        // Case B: LLM suggested intakeComplete = true, check if essential information exists
        if (aiIntakeComplete) {
            int requiredCount = getDimensionsForCategory(category).size();
            int satisfiedCount = requiredCount - missingCritical.size();
            
            boolean hasCoreProblem = isAnyDimensionSatisfied(List.of("core_problem", "incident", "offence_type", "issue", "dispute_details"), facts, city, state);
            
            // If at least 50% of dimensions (or 3+ dimensions) are satisfied and core problem is known, accept completion
            if (hasCoreProblem && satisfiedCount >= Math.max(3, (int)(requiredCount * 0.5))) {
                log.info("LLM marked complete and essential dimensions ({}/{}) satisfied. Accepting completion.",
                        satisfiedCount, requiredCount);
                return true;
            } else {
                log.warn("LLM proposed intakeComplete=true, but essential core dimensions are missing: {}. Backend rejects.",
                        missingCritical);
                return false;
            }
        }

        // Case C: AI did not propose completion, but backend determines sufficient information exists
        // This prevents customer fatigue by stopping when enough important facts are collected
        {
            int requiredCount = getDimensionsForCategory(category).size();
            int satisfiedCount = requiredCount - missingCritical.size();
            boolean hasCoreProblem = isAnyDimensionSatisfied(List.of("core_problem", "incident", "issue", "offence_type", "dispute_details", "dispute_nature"), facts, city, state);

            // Category-specific essential dimension check
            boolean hasEssentialCategoryFact = true;
            if (category == LegalCategory.FAMILY) {
                hasEssentialCategoryFact = isAnyDimensionSatisfied(List.of("marriage_status", "marriage_details", "marriage_date", "relationship_timeline"), facts, city, state);
            }

            // If 80%+ of dimensions are satisfied, core problem is known, and essential category fact is present
            if (hasCoreProblem && hasEssentialCategoryFact && satisfiedCount >= Math.max(4, (int)(requiredCount * 0.8))) {
                log.info("Backend sufficiency check: {}/{} dimensions satisfied with core problem and essential facts known. Declaring complete.",
                        satisfiedCount, requiredCount);
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the list of critical dimension keys that are currently missing.
     */
    public List<String> getMissingCriticalFacts(LegalCategory category,
                                                Map<String, Object> facts,
                                                String city,
                                                String state) {
        List<List<String>> dimensions = getDimensionsForCategory(category);
        List<String> missing = new ArrayList<>();

        for (List<String> dimensionAliases : dimensions) {
            if (!isAnyDimensionSatisfied(dimensionAliases, facts, city, state)) {
                // Pick the primary alias as the identifier
                missing.add(dimensionAliases.get(0));
            }
        }
        return missing;
    }

    private List<List<String>> getDimensionsForCategory(LegalCategory category) {
        if (category != null && REQUIRED_DIMENSIONS.containsKey(category)) {
            return REQUIRED_DIMENSIONS.get(category);
        }
        return DEFAULT_DIMENSIONS;
    }

    private boolean isAnyDimensionSatisfied(List<String> aliases,
                                            Map<String, Object> facts,
                                            String city,
                                            String state) {
        if (facts == null) facts = Map.of();

        boolean isEvidenceDim = aliases.stream().anyMatch(a -> a.contains("evidence") || a.contains("proof") || a.contains("document"));
        boolean isPoliceDim = aliases.stream().anyMatch(a -> a.contains("police") || a.contains("fir") || a.contains("complaint") || a.contains("legal_action"));

        for (String alias : aliases) {
            if ("jurisdiction".equalsIgnoreCase(alias) || "jurisdiction_city".equalsIgnoreCase(alias) || "city".equalsIgnoreCase(alias)) {
                if (factMergeService.isMeaningfulValue(city) || factMergeService.isMeaningfulValue(state)) {
                    return true;
                }
            }
            if (facts.containsKey(alias)) {
                Object val = facts.get(alias);
                if (factMergeService.isMeaningfulValue(val)) {
                    return true;
                }
            }
        }

        // Fuzzy dimension matching for evidence: if ANY key in collectedFacts relates to evidence/proof/whatsapp/chat/screenshot/document and has a meaningful value
        if (isEvidenceDim) {
            for (Map.Entry<String, Object> entry : facts.entrySet()) {
                String keyLower = entry.getKey().toLowerCase();
                if ((keyLower.contains("evidence") || keyLower.contains("proof") || keyLower.contains("whatsapp") || keyLower.contains("chat") || keyLower.contains("screenshot") || keyLower.contains("document") || keyLower.contains("receipt") || keyLower.contains("fir_copy") || keyLower.contains("copy"))
                        && factMergeService.isMeaningfulValue(entry.getValue())) {
                    return true;
                }
            }
        }

        // Fuzzy dimension matching for police / legal action: if ANY key in collectedFacts relates to police/fir/complaint/station/legal_action
        if (isPoliceDim) {
            for (Map.Entry<String, Object> entry : facts.entrySet()) {
                String keyLower = entry.getKey().toLowerCase();
                if ((keyLower.contains("police") || keyLower.contains("fir") || keyLower.contains("complaint") || keyLower.contains("station") || keyLower.contains("legal_action"))
                        && factMergeService.isMeaningfulValue(entry.getValue())) {
                    return true;
                }
            }
        }

        return false;
    }
}
