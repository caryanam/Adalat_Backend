package com.adalat.service.ai.engine;

import com.adalat.enums.LegalCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntiLoopEngine {

    private final FactMergeService factMergeService;

    private static final Set<String> STOP_WORDS = Set.of(
            "is", "are", "was", "were", "do", "does", "did", "you", "your", "have", "has", "had",
            "the", "a", "an", "any", "or", "and", "some", "what", "which", "when", "where", "how",
            "to", "in", "for", "of", "on", "at", "by", "with", "about", "can", "tell", "more",
            "ka", "ki", "ko", "ke", "hai", "hain", "kya", "aahe", "hot", "koni", "ani", "tar",
            "ahe", "ahi", "nahi", "nahit", "kay", "kasa", "kashi", "kase", "mhanun", "mala",
            "tula", "tyala", "tila", "samajla", "samajle", "sang", "sanga", "sangal",
            "kahi", "kahihi", "sagla", "ho", "nai", "re", "na", "mag", "mg", "baddal", "babat"
    );

    /**
     * Inspects a proposed question and fact key against the session history.
     * Returns true if the question or key is a duplicate or targets an already-known fact.
     */
    public boolean isDuplicateQuestion(String nextQuestionFactKey,
                                       String nextQuestion,
                                       Set<String> askedFactKeys,
                                       Map<String, Object> collectedFacts,
                                       List<String> askedQuestions) {

        // 1. nextQuestionFactKey must not be null or blank when nextQuestion exists
        if (nextQuestion != null && !nextQuestion.isBlank()) {
            if (nextQuestionFactKey == null || nextQuestionFactKey.isBlank()) {
                log.warn("AntiLoop: nextQuestion provided without a valid nextQuestionFactKey.");
                return true;
            }
        } else {
            return false;
        }

        String normalizedKey = nextQuestionFactKey.trim().toLowerCase();

        // 2. Check if nextQuestionFactKey was already asked
        if (askedFactKeys != null && askedFactKeys.contains(normalizedKey)) {
            log.warn("AntiLoop: Fact key '{}' was already in askedFactKeys.", normalizedKey);
            return true;
        }

        // 3. Check if collectedFacts already contains a meaningful answer for this fact key
        if (collectedFacts != null && collectedFacts.containsKey(normalizedKey)) {
            Object existingVal = collectedFacts.get(normalizedKey);
            if (factMergeService.isMeaningfulValue(existingVal)) {
                log.warn("AntiLoop: Fact key '{}' already has meaningful answer: {}", normalizedKey, existingVal);
                return true;
            }
        }

        // 3b. Check by semantic dimension: Evidence availability
        boolean isEvidenceQuestion = normalizedKey.contains("evidence") || normalizedKey.contains("proof") || normalizedKey.contains("document") || normalizedKey.contains("receipt");
        if (!isEvidenceQuestion && nextQuestion != null) {
            String lowerQ = nextQuestion.toLowerCase();
            isEvidenceQuestion = lowerQ.contains("पुरावा") || lowerQ.contains("evidence") || lowerQ.contains("proof") || lowerQ.contains("पावती") || lowerQ.contains("screenshot") || lowerQ.contains("document");
        }
        if (isEvidenceQuestion && hasEvidenceInCollectedFacts(collectedFacts)) {
            log.warn("AntiLoop: Evidence dimension is already satisfied in collectedFacts. Rejecting proposed evidence question.");
            return true;
        }

        // 3c. Check by semantic dimension: Police / FIR status
        boolean isPoliceQuestion = normalizedKey.contains("police") || normalizedKey.contains("fir") || normalizedKey.contains("complaint");
        if (!isPoliceQuestion && nextQuestion != null) {
            String lowerQ = nextQuestion.toLowerCase();
            isPoliceQuestion = lowerQ.contains("पोलीस") || lowerQ.contains("तक्रार") || lowerQ.contains("fir") || lowerQ.contains("police");
        }
        if (isPoliceQuestion && hasPoliceInCollectedFacts(collectedFacts)) {
            log.warn("AntiLoop: Police/FIR dimension is already satisfied in collectedFacts. Rejecting proposed police question.");
            return true;
        }

        // 4. Check normalized question text and semantic similarity against previously asked questions
        if (askedQuestions != null && !askedQuestions.isEmpty()) {
            String cleanProposed = normalizeText(nextQuestion);
            Set<String> proposedTokens = extractMeaningfulTokens(cleanProposed);

            for (String pastQuestion : askedQuestions) {
                String cleanPast = normalizeText(pastQuestion);

                // Exact normalized text match
                if (cleanProposed.equals(cleanPast)) {
                    log.warn("AntiLoop: Question text is exact normalized duplicate of past question: '{}'", pastQuestion);
                    return true;
                }

                // Token Jaccard overlap & Containment overlap for semantic duplication check
                Set<String> pastTokens = extractMeaningfulTokens(cleanPast);
                double jaccard = calculateJaccardSimilarity(proposedTokens, pastTokens);
                double overlap = calculateOverlapCoefficient(proposedTokens, pastTokens);

                // For a genuinely NEW fact key, apply stricter threshold so we don't falsely reject different questions sharing common context words
                double jaccardThreshold = 0.75;
                double overlapThreshold = 0.85;

                if (jaccard >= jaccardThreshold || (overlap >= overlapThreshold && proposedTokens.size() >= 3)) {
                    log.warn("AntiLoop: Semantic duplicate detected (jaccard={}, overlap={}): '{}' vs '{}'",
                            jaccard, overlap, nextQuestion, pastQuestion);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Provides a safe deterministic fallback question for a missing critical fact key
     * when the AI is repeating questions or failing.
     */
    public String getFallbackQuestion(String missingFactKey) {
        return getFallbackQuestion(null, missingFactKey, "MARATHI", null);
    }

    public String getFallbackQuestion(String missingFactKey, String language) {
        return getFallbackQuestion(null, missingFactKey, language, null);
    }

    public String getFallbackQuestion(LegalCategory category, String missingFactKey, String language, Map<String, Object> collectedFacts) {
        String lang = language != null ? language.toUpperCase().trim() : "MARATHI";

        // Check if category or collectedFacts indicate a criminal harassment/threat matter
        boolean isCriminalOrThreat = (category == LegalCategory.CRIMINAL) || hasThreatOrHarassment(collectedFacts);

        if (isCriminalOrThreat) {
            return getCriminalFallbackQuestion(missingFactKey, lang, collectedFacts);
        }

        if ("ENGLISH".equals(lang)) {
            return getEnglishFallbackQuestion(missingFactKey, collectedFacts);
        } else if ("HINDI".equals(lang)) {
            return getHindiFallbackQuestion(missingFactKey, collectedFacts);
        } else {
            return getMarathiFallbackQuestion(missingFactKey, collectedFacts);
        }
    }

    private boolean hasThreatOrHarassment(Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) return false;
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            String valStr = String.valueOf(entry.getValue()).toLowerCase();
            if (valStr.contains("threat") || valStr.contains("dhamki") || valStr.contains("abuse") || valStr.contains("shivya") || valStr.contains("harass") || valStr.contains("maraychya")) {
                return true;
            }
        }
        return false;
    }

    private String getCriminalFallbackQuestion(String missingFactKey, String lang, Map<String, Object> collectedFacts) {
        boolean ongoingAsked = isFactKnown(collectedFacts, "threat_ongoing", "calls_continuing", "threat_status");
        boolean locationKnowledgeAsked = isFactKnown(collectedFacts, "knows_location", "knows_residence", "accused_knows_location");
        boolean immediateDangerAsked = isFactKnown(collectedFacts, "immediate_danger", "safety_concern", "danger_level");

        if (!ongoingAsked) {
            if ("ENGLISH".equals(lang)) return "Are threatening calls or messages currently continuing?";
            if ("HINDI".equals(lang)) return "क्या धमकी भरे कॉल या मैसेज अभी भी आ रहे हैं?";
            return "धमकीचे कॉल किंवा मेसेज अजूनही सुरू आहेत का?";
        }

        if (!locationKnowledgeAsked) {
            if ("ENGLISH".equals(lang)) return "Does that person know your current location or residence?";
            if ("HINDI".equals(lang)) return "क्या उस व्यक्ति को आपका वर्तमान स्थान या पता मालूम है?";
            return "त्या व्यक्तीला तुमची सध्याची जागा किंवा राहण्याचे ठिकाण माहिती आहे का?";
        }

        if (!immediateDangerAsked) {
            if ("ENGLISH".equals(lang)) return "Do you currently feel in immediate physical danger?";
            if ("HINDI".equals(lang)) return "क्या आपको अभी अपनी सुरक्षा को लेकर तुरंत कोई खतरा महसूस हो रहा है?";
            return "तुम्हाला सध्या स्वतःच्या सुरक्षिततेबाबत तातडीची भीती वाटते आहे का?";
        }

        return null;
    }

    private boolean isFactKnown(Map<String, Object> facts, String... keys) {
        if (facts == null || facts.isEmpty()) return false;
        for (String k : keys) {
            if (facts.containsKey(k) && factMergeService.isMeaningfulValue(facts.get(k))) {
                return true;
            }
        }
        return false;
    }

    private String getEnglishFallbackQuestion(String missingFactKey, Map<String, Object> collectedFacts) {
        if (missingFactKey == null) return null;
        String key = missingFactKey.toLowerCase();
        if (key.equals("child_access_status") || key.equals("child_involvement")) {
            return "Are you currently able to meet or communicate with your child?";
        } else if (key.equals("legal_action_status")) {
            return "Has any legal notice, police complaint, or court case been filed so far by either party?";
        } else if (key.equals("desired_outcome") || key.equals("remedy_sought")) {
            return "What primary outcome are you seeking — advocate advice, financial compensation, or legal proceedings?";
        } else if (key.equals("jurisdiction") || key.contains("city") || key.contains("location")) {
            if (isFactKnown(collectedFacts, "jurisdiction_city", "city", "location")) return null;
            return "Which city or state did this incident occur in?";
        } else if (key.equals("marriage_status") || key.contains("marriage")) {
            return "Is your marriage legally registered, and how long ago were you married?";
        } else if (key.equals("merchant") || key.contains("company") || key.contains("seller") || key.contains("product")) {
            return "What is the name of the store, company, or merchant involved, and in which city are they located?";
        } else if (key.equals("purchase") || key.contains("amount") || key.contains("bill") || key.contains("payment")) {
            return "What was the total transaction amount, and do you have a bill or payment receipt?";
        } else if (key.contains("police") || key.contains("fir") || key.contains("station")) {
            if (isFactKnown(collectedFacts, "police_status", "police_complaint_status", "fir_status", "police_involvement")) return null;
            return "Have you filed a verbal or written complaint (FIR) with the local police regarding this incident?";
        } else if (key.contains("injury") || key.contains("harm")) {
            if (isFactKnown(collectedFacts, "injury_status", "physical_harm")) return null;
            return "Was anyone physically injured or in need of medical treatment in this incident?";
        } else if (key.contains("agreement") || key.contains("contract")) {
            return "Do you have a written agreement, contract, or lease documents for this matter?";
        } else if (key.contains("evidence") || key.contains("proof")) {
            if (hasEvidenceInCollectedFacts(collectedFacts)) return null;
            return "Do you have photos, WhatsApp chats, receipts, or other evidence available for this incident?";
        } else if (key.contains("salary") || key.contains("period")) {
            return "How many months of salary or for what duration is payment pending?";
        } else if (key.contains("employer") || key.contains("hr")) {
            return "What was the response when you asked HR or management for payment?";
        } else if (key.contains("notice") || key.contains("eviction")) {
            return "Have you been given any written notice or deadline?";
        }
        return null;
    }

    private String getHindiFallbackQuestion(String missingFactKey, Map<String, Object> collectedFacts) {
        if (missingFactKey == null) return null;
        String key = missingFactKey.toLowerCase();
        if (key.equals("child_access_status") || key.equals("child_involvement")) {
            return "क्या आप फिलहाल बच्चे से मिल सकते हैं या बात कर सकते हैं?";
        } else if (key.equals("legal_action_status")) {
            return "क्या आपकी या आपकी पत्नी की ओर से अब तक कोई लीगल नोटिस, पुलिस शिकायत या कोर्ट केस दर्ज हुआ है?";
        } else if (key.equals("desired_outcome") || key.equals("remedy_sought")) {
            return "आप इस मामले में मुख्य रूप से क्या चाहते हैं — वकील की सलाह, मुआवजा, या कानूनी कार्रवाई?";
        } else if (key.equals("jurisdiction") || key.contains("city") || key.contains("location")) {
            if (isFactKnown(collectedFacts, "jurisdiction_city", "city", "location")) return null;
            return "यह घटना किस शहर या राज्य की है?";
        } else if (key.equals("marriage_status") || key.equals("marriage")) {
            return "क्या आपकी शादी रजिस्टर्ड है और लगभग कितने साल पहले हुई थी?";
        } else if (key.contains("merchant") || key.contains("company") || key.contains("seller") || key.contains("product")) {
            return "संबंधित दुकान, कंपनी या मर्चेंट का नाम और शहर क्या है?";
        } else if (key.contains("purchase") || key.contains("amount") || key.contains("bill") || key.contains("payment")) {
            return "कुल लेनदेन की राशि कितनी थी और क्या आपके पास बिल या रसीद है?";
        } else if (key.contains("police") || key.contains("fir") || key.contains("station")) {
            if (isFactKnown(collectedFacts, "police_status", "police_complaint_status", "fir_status", "police_involvement")) return null;
            return "क्या आपने इस घटना के संबंध में पुलिस थाने में कोई शिकायत या एफआईआर दर्ज कराई है?";
        } else if (key.contains("injury") || key.contains("harm")) {
            if (isFactKnown(collectedFacts, "injury_status", "physical_harm")) return null;
            return "क्या इस घटना में किसी को शारीरिक चोट या चोट आई है?";
        } else if (key.contains("agreement") || key.contains("contract")) {
            return "क्या आपके पास कोई लिखित समझौता, एग्रीमेंट या दस्तावेज है?";
        } else if (key.contains("evidence") || key.contains("proof")) {
            if (hasEvidenceInCollectedFacts(collectedFacts)) return null;
            return "क्या आपके पास इस घटना से संबंधित कोई फोटो, व्हाट्सएप चैट, बिल या अन्य सबूत है?";
        } else if (key.contains("salary") || key.contains("period")) {
            return "आपका कितने महीनों का वेतन बकाया है?";
        } else if (key.contains("employer") || key.contains("hr")) {
            return "जब आपने एचआर या कंपनी से बात की तो उनका क्या जवाब था?";
        } else if (key.contains("notice") || key.contains("eviction")) {
            return "क्या आपको कोई लिखित नोटिस या समय सीमा दी गई है?";
        }
        return null;
    }

    private String getMarathiFallbackQuestion(String missingFactKey, Map<String, Object> collectedFacts) {
        if (missingFactKey == null) return null;

        String key = missingFactKey.toLowerCase();

        if (key.equals("child_access_status") || key.equals("child_involvement")) {
            return "सध्या तुम्हाला मुलाला भेटता किंवा त्याच्याशी बोलता येतंय का?";
        } else if (key.equals("legal_action_status")) {
            return "तुमच्या किंवा तुमच्या पत्नीच्या वतीने आतापर्यंत कोणती legal notice, police complaint किंवा court case दाखल झाली आहे का?";
        } else if (key.equals("desired_outcome") || key.equals("remedy_sought")) {
            return "या प्रकरणात तुम्हाला मुख्यत्वे काय हवं आहे — ॲडव्होकेट सल्ला, नुकसानभरपाई, की कायदेशीर कारवाई?";
        } else if (key.equals("jurisdiction") || key.contains("city") || key.contains("location")) {
            if (isFactKnown(collectedFacts, "jurisdiction_city", "city", "location")) return null;
            return "ही घटना किंवा तुमचे प्रकरण नक्की कोणत्या शहरातील/राज्यातील आहे?";
        } else if (key.equals("marriage_status") || key.equals("marriage")) {
            return "तुमचं लग्न legally registered आहे का आणि साधारण किती वर्षांपूर्वी झालं?";
        }

        if (key.contains("merchant") || key.contains("company") || key.contains("seller") || key.contains("product")) {
            return "संबंधित दुकान, मेडिकल स्टोअर किंवा कंपनीचे नाव काय आहे आणि ते कोणत्या शहरातील/भागात आहे?";
        } else if (key.contains("purchase") || key.contains("amount") || key.contains("bill") || key.contains("payment")) {
            return "या व्यवहाराची एकूण रक्कम किती होती आणि तुमच्याकडे बिल किंवा पेमेंटची पावती आहे का?";
        }

        if (key.contains("police") || key.contains("fir") || key.contains("station")) {
            if (isFactKnown(collectedFacts, "police_status", "police_complaint_status", "fir_status", "police_involvement")) return null;
            return "या घटनेबाबत तुम्ही आधी जवळच्या पोलीस ठाण्यात काही तोंडी किंवा लेखी तक्रार (FIR) दिली आहे का?";
        } else if (key.contains("injury") || key.contains("harm")) {
            if (isFactKnown(collectedFacts, "injury_status", "physical_harm")) return null;
            return "या घटनेमध्ये कोणाला काही शारीरिक दुखापत किंवा वैद्यकीय अडचण निर्माण झाली आहे का?";
        }

        if (key.contains("agreement") || key.contains("contract")) {
            return "या संदर्भात तुमच्याकडे काही लेखी करार, ॲग्रीमेंट किंवा कागदपत्रे आहेत का?";
        } else if (key.contains("evidence") || key.contains("proof")) {
            if (hasEvidenceInCollectedFacts(collectedFacts)) return null;
            return "या घटनेबाबत तुमच्याकडे काही फोटो, व्हॉट्सअॅप चॅट, बिल किंवा इतर पुरावा उपलब्ध आहे का?";
        } else if (key.contains("salary") || key.contains("period")) {
            return "तुमचे नक्की किती महिन्यांचे किंवा किती कालावधीचे पगार बाकी आहेत?";
        } else if (key.contains("employer") || key.contains("hr")) {
            return "तुम्ही एचआर किंवा मालकाकडे मागणी केली तेव्हा त्यांनी काय उत्तर दिले?";
        } else if (key.contains("notice") || key.contains("eviction")) {
            return "तुम्हाला कोणतीही लेखी नोटीस किंवा मुदत दिली गेली आहे का?";
        }

        return null;
    }

    public String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> extractMeaningfulTokens(String cleanText) {
        Set<String> tokens = new HashSet<>();
        for (String word : cleanText.split("\\s+")) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public boolean hasEvidenceInCollectedFacts(Map<String, Object> collectedFacts) {
        if (collectedFacts == null || collectedFacts.isEmpty()) return false;
        for (Map.Entry<String, Object> entry : collectedFacts.entrySet()) {
            String keyLower = entry.getKey().toLowerCase();
            if ((keyLower.contains("evidence") || keyLower.contains("proof") || keyLower.contains("whatsapp") || keyLower.contains("chat") || keyLower.contains("screenshot") || keyLower.contains("document") || keyLower.contains("receipt"))
                    && factMergeService.isMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPoliceInCollectedFacts(Map<String, Object> collectedFacts) {
        if (collectedFacts == null || collectedFacts.isEmpty()) return false;
        for (Map.Entry<String, Object> entry : collectedFacts.entrySet()) {
            String keyLower = entry.getKey().toLowerCase();
            if ((keyLower.contains("police") || keyLower.contains("fir") || keyLower.contains("complaint") || keyLower.contains("station") || keyLower.contains("legal_action"))
                    && factMergeService.isMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private double calculateJaccardSimilarity(Set<String> s1, Set<String> s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        return (double) intersection.size() / (double) union.size();
    }

    private double calculateOverlapCoefficient(Set<String> s1, Set<String> s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        return (double) intersection.size() / (double) Math.min(s1.size(), s2.size());
    }
}
