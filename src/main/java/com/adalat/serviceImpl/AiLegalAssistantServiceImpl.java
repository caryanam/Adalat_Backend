package com.adalat.serviceImpl;

import com.adalat.dto.LegalCategoryResultResponseDTO;
import com.adalat.entity.LegalAnswer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;
import com.adalat.service.AiLegalAssistantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class AiLegalAssistantServiceImpl implements AiLegalAssistantService {

    public static final String LEGAL_DISCLAIMER =
            "AI categorization and summaries are for informational and preparation purposes only and do not constitute legal advice. Please consult a verified lawyer for formal legal representation.";

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String openAiModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public LegalCategoryResultResponseDTO detectCategoryFromText(String problemText, Long sessionId) {
        if (problemText == null || problemText.isBlank()) {
            LegalCategory defaultCat = LegalCategory.OTHER;
            return buildCategoryResult(defaultCat, sessionId, 0.50, "General inquiry");
        }

        // Try OpenAI categorization if key is configured
        if (openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.startsWith("${")) {
            try {
                LegalCategoryResultResponseDTO openAiResult = callOpenAiForCategory(problemText, sessionId);
                if (openAiResult != null) {
                    return openAiResult;
                }
            } catch (Exception e) {
                log.warn("OpenAI API categorization call failed, falling back to built-in rule engine: {}", e.getMessage());
            }
        }

        return fallbackDetectCategory(problemText, sessionId);
    }

    private LegalCategoryResultResponseDTO callOpenAiForCategory(String problemText, Long sessionId) {
        try {
            String prompt = """
                    You are an Indian legal assistant. Analyze the user problem and classify it into EXACTLY ONE of these categories:
                    PROPERTY_RENTAL_DISPUTE, DIVORCE, CRIMINAL_MATTER, WORKPLACE_ISSUE, CONSUMER_COMPLAINT, CYBERCRIME, FAMILY_DISPUTE, CIVIL_DISPUTE, MATRIMONIAL_MATTER, BANKING_FINANCE, EMPLOYMENT_DISPUTE, CORPORATE_MATTER, OTHER.
                    
                    Return ONLY a JSON object in this format:
                    {"category": "CATEGORY_NAME", "confidence": 0.95, "summary": "Brief 1 sentence assessment"}
                    
                    Problem: "%s"
                    """.formatted(problemText.replace("\"", "\\\""));

            Map<String, Object> requestBody = Map.of(
                    "model", openAiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are an Indian legal intake classifier. Respond in JSON only."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.2
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openAiApiKey.trim())
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String content = root.path("choices").get(0).path("message").path("content").asText();
                JsonNode parsedJson = objectMapper.readTree(content);
                String catName = parsedJson.path("category").asText();
                double confidence = parsedJson.path("confidence").asDouble(0.90);
                String summary = parsedJson.path("summary").asText("Categorized by OpenAI");

                LegalCategory category = LegalCategory.valueOf(catName);
                log.info("OpenAI successfully classified problem into category: {}", category);
                return buildCategoryResult(category, sessionId, confidence, summary);
            } else {
                log.warn("OpenAI API returned status: {} body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Error communicating with OpenAI: {}", e.getMessage());
        }
        return null;
    }

    private LegalCategoryResultResponseDTO fallbackDetectCategory(String problemText, Long sessionId) {
        String lower = problemText.toLowerCase();
        LegalCategory detectedCategory;
        double confidence;

        if (lower.contains("rent") || lower.contains("landlord") || lower.contains("tenant") || lower.contains("deposit") ||
                lower.contains("evict") || lower.contains("flat") || lower.contains("property") || lower.contains("plot") || lower.contains("lease")) {
            detectedCategory = LegalCategory.PROPERTY_RENTAL_DISPUTE;
            confidence = 0.92;
        } else if (lower.contains("divorce") || lower.contains("separation") || lower.contains("spouse") || lower.contains("husband") ||
                lower.contains("wife") || lower.contains("alimony") || lower.contains("maintenance") || lower.contains("custody")) {
            detectedCategory = LegalCategory.DIVORCE;
            confidence = 0.94;
        } else if (lower.contains("police") || lower.contains("fir") || lower.contains("arrest") || lower.contains("bail") ||
                lower.contains("assault") || lower.contains("theft") || lower.contains("threat") || lower.contains("crime") || lower.contains("complaint against")) {
            detectedCategory = LegalCategory.CRIMINAL_MATTER;
            confidence = 0.91;
        } else if (lower.contains("salary") || lower.contains("employer") || lower.contains("boss") || lower.contains("terminated") ||
                lower.contains("laid off") || lower.contains("workplace") || lower.contains("pf") || lower.contains("gratuity") || lower.contains("harassment at work")) {
            detectedCategory = LegalCategory.WORKPLACE_ISSUE;
            confidence = 0.89;
        } else if (lower.contains("defective") || lower.contains("warranty") || lower.contains("refund") || lower.contains("consumer") ||
                lower.contains("e-commerce") || lower.contains("product") || lower.contains("damaged") || lower.contains("service deficiency")) {
            detectedCategory = LegalCategory.CONSUMER_COMPLAINT;
            confidence = 0.90;
        } else if (lower.contains("cyber") || lower.contains("hacked") || lower.contains("online fraud") || lower.contains("otp") ||
                lower.contains("scam") || lower.contains("phishing") || lower.contains("identity theft") || lower.contains("blackmail online")) {
            detectedCategory = LegalCategory.CYBERCRIME;
            confidence = 0.93;
        } else if (lower.contains("ancestral") || lower.contains("will") || lower.contains("partition") || lower.contains("inheritance") ||
                lower.contains("family property") || lower.contains("succession")) {
            detectedCategory = LegalCategory.FAMILY_DISPUTE;
            confidence = 0.88;
        } else if (lower.contains("cheque") || lower.contains("loan") || lower.contains("emi") || lower.contains("bank") ||
                lower.contains("nbfc") || lower.contains("recovery agent") || lower.contains("debt")) {
            detectedCategory = LegalCategory.BANKING_FINANCE;
            confidence = 0.90;
        } else if (lower.contains("contract") || lower.contains("agreement breach") || lower.contains("recovery") || lower.contains("money suit") ||
                lower.contains("notice")) {
            detectedCategory = LegalCategory.CIVIL_DISPUTE;
            confidence = 0.87;
        } else if (lower.contains("company") || lower.contains("director") || lower.contains("partner") || lower.contains("shareholder") ||
                lower.contains("trademark") || lower.contains("gst")) {
            detectedCategory = LegalCategory.CORPORATE_MATTER;
            confidence = 0.85;
        } else {
            detectedCategory = LegalCategory.OTHER;
            confidence = 0.65;
        }

        String summary = "Based on your description, the primary matter relates to " + detectedCategory.getDisplayName() + ".";
        return buildCategoryResult(detectedCategory, sessionId, confidence, summary);
    }

    private LegalCategoryResultResponseDTO buildCategoryResult(LegalCategory category, Long sessionId, double confidence, String summary) {
        PracticeArea practiceArea = mapCategoryToPracticeArea(category);

        return LegalCategoryResultResponseDTO.builder()
                .sessionId(sessionId)
                .likelyLegalCategory(category)
                .categoryDisplayName(category.getDisplayName())
                .relevantPracticeArea(practiceArea)
                .practiceAreaDisplayName(practiceArea.name().replace("_", " "))
                .summary(summary)
                .confidence(confidence)
                .disclaimer(LEGAL_DISCLAIMER)
                .build();
    }

    @Override
    public List<String> getQuestionsForCategory(LegalCategory category) {
        if (category == null) {
            category = LegalCategory.OTHER;
        }

        return switch (category) {
            case PROPERTY_RENTAL_DISPUTE -> List.of(
                    "What is the primary issue regarding the property (e.g., security deposit refund, illegal eviction, lease violation, property damage)?",
                    "What is your relationship to the property (owner, tenant, landlord, buyer, or licensee)?",
                    "Do you have an active or signed rental agreement, sale deed, or lease document?",
                    "Approximately when did this dispute begin or when was the last payment/deposit made?",
                    "What is the disputed financial amount or security deposit value involved (if applicable)?",
                    "Have you or the other party issued or received any formal written communication or legal notice?",
                    "Have you already filed a police complaint, approached a rent control authority, or filed a civil court petition?",
                    "What is your desired outcome or resolution (e.g., full refund of deposit, peaceful possession, compensation, tenancy termination)?"
            );

            case DIVORCE, MATRIMONIAL_MATTER -> List.of(
                    "What is the primary reason for seeking matrimonial legal assistance (e.g., mutual consent divorce, contested divorce, judicial separation, domestic disputes)?",
                    "How long have you been married, and under which marriage act (e.g., Hindu Marriage Act, Special Marriage Act, Muslim Personal Law)?",
                    "Are you and your spouse currently living together or separated? If separated, for how long?",
                    "Do you have any children from the marriage? If yes, please mention their ages and who currently has custody.",
                    "Is this proceeding likely to be on mutual consent, or is it contested by either party?",
                    "Are there ongoing claims or discussions regarding maintenance, alimony, or return of Streedhan/gifts?",
                    "Are there jointly owned properties, shared bank accounts, or financial liabilities involved?",
                    "Have any police complaints (such as 498A), domestic violence petitions, or mediation proceedings already taken place?"
            );

            case CRIMINAL_MATTER -> List.of(
                    "What is the nature of the alleged offence or criminal incident (e.g., assault, theft, cheating/fraud, threat, harassment)?",
                    "Are you the victim/complainant seeking to file a complaint, or the accused seeking bail/defense?",
                    "When and where did the incident occur?",
                    "Has a formal Police Complaint or FIR (First Information Report) been registered? If yes, at which police station?",
                    "Has any arrest been made, or has a notice under Section 41A CrPC / BNSS been served?",
                    "Are there any eyewitnesses, CCTV recordings, medical examination reports, or audio/video evidence available?",
                    "Has any application for Anticipatory Bail or Regular Bail been filed in court?",
                    "What immediate legal relief do you require (e.g., urgent bail, filing an FIR, quashing an FIR, court protection)?"
            );

            case WORKPLACE_ISSUE, EMPLOYMENT_DISPUTE -> List.of(
                    "What is the core workplace dispute (e.g., unpaid salary, wrongful termination, notice period dispute, harassment, PF/gratuity denial)?",
                    "How long were you/have you been employed with this organization, and what was your role/designation?",
                    "Do you have an employment contract, offer letter, salary slips, and company email communications?",
                    "Did the employer provide any written termination notice, PIP (Performance Improvement Plan), or show-cause letter?",
                    "What is the outstanding financial compensation, severance, or unpaid salary amount?",
                    "Have you raised an internal grievance with HR, ICC (Internal Complaints Committee), or management?",
                    "Have you received or served any legal notice or approached the Labour Commissioner?",
                    "What specific relief are you seeking (e.g., recovery of dues, experience certificate, reinstatement, damages)?"
            );

            case CONSUMER_COMPLAINT -> List.of(
                    "What product or service did you purchase, and from which company/seller?",
                    "What is the exact defect in the product or deficiency in the service provided?",
                    "When was the transaction made, and do you possess the invoice/bill, payment receipt, or warranty card?",
                    "What is the total financial amount spent or the loss suffered due to this issue?",
                    "Did you lodge a formal complaint with the company's customer support, and what was their response?",
                    "Has the company refused replacement, repair, or refund?",
                    "Have you sent a formal legal notice to the manufacturer or service provider?",
                    "What remedy are you seeking from the Consumer Forum (e.g., full refund, replacement, compensation for mental harassment)?"
            );

            case CYBERCRIME -> List.of(
                    "What type of cyber incident occurred (e.g., financial fraud/OTP scam, unauthorized account access, cyber stalking, identity theft, data breach)?",
                    "What is the financial loss incurred, and what was the transaction medium (UPI, Net Banking, Credit Card, Crypto)?",
                    "On what date and time did the incident occur?",
                    "Have you immediately informed your bank to freeze or reverse the transaction?",
                    "Have you registered a complaint on the National Cyber Crime Reporting Portal (cybercrime.gov.in) or dialled 1930?",
                    "Do you possess transaction IDs, bank statements, scammer phone numbers, URLs, or chat screenshots?",
                    "Have you visited the local Cyber Crime Police Station or filed a physical police complaint?",
                    "What immediate assistance do you need (e.g., freezing fraud accounts, filing an FIR, legal representation)?"
            );

            case FAMILY_DISPUTE -> List.of(
                    "What is the nature of the family dispute (e.g., ancestral property partition, will probate, succession certificate, gift deed challenge)?",
                    "Who originally acquired or owned the disputed property or assets, and is there a registered Will?",
                    "What is your relationship to the deceased or the other family members involved?",
                    "How many legal heirs/claimants are there in total, and what are their stances?",
                    "Is the property currently in your possession or in the possession of another family member?",
                    "Are there any revenue records, mutation certificates, or title deeds available in your custody?",
                    "Has any partition suit or injunction application been filed in the civil court?",
                    "What specific resolution are you seeking (e.g., legal share in property, stay order against sale, succession certificate)?"
            );

            case BANKING_FINANCE -> List.of(
                    "What is the financial issue (e.g., loan recovery harassment, cheque bounce under Sec 138 NI Act, CIBIL dispute, illegal account freeze)?",
                    "Which bank, NBFC, or financial institution is involved?",
                    "What is the total loan or disputed transaction amount?",
                    "Have you received a notice under Section 138 (Cheque Bounce), SARFAESI Act, or a DRT summons?",
                    "Are recovery agents resorting to harassment, threats, or unauthorized visits?",
                    "Do you have loan sanction letters, repayment receipts, and communication records?",
                    "Have you approached the Banking Ombudsman or attempted a one-time settlement (OTS)?",
                    "What is your primary objective (e.g., stopping harassment, debt restructuring, defending court notice, account unfreeze)?"
            );

            case CORPORATE_MATTER -> List.of(
                    "What is the corporate/commercial matter (e.g., shareholder/director dispute, contract breach, trademark infringement, insolvency/NCLT)?",
                    "What is the legal structure of the entities involved (Private Limited, LLP, Partnership, Sole Proprietorship)?",
                    "Do you have the executed Agreement/Contract (e.g., Founders Agreement, Vendor Contract, NDA, SLA)?",
                    "What is the total commercial valuation or financial claim involved in this dispute?",
                    "Is there a valid Arbitration clause or specific dispute resolution mechanism in the contract?",
                    "Has any notice of default, termination, or demand notice been issued by either party?",
                    "Are there any ongoing proceedings before the NCLT, High Court, or an Arbitral Tribunal?",
                    "What specific commercial or legal remedy is your company seeking?"
            );

            case CIVIL_DISPUTE, OTHER -> List.of(
                    "Please describe the primary dispute or legal challenge you are currently facing in detail.",
                    "Who are the other parties involved (individuals, business entities, government authorities)?",
                    "What documents, agreements, receipts, or written records exist in relation to this matter?",
                    "When did the dispute or cause of action first arise?",
                    "What is the financial value or property involved in this dispute (if any)?",
                    "Have any legal notices, police complaints, or court petitions been filed to date?",
                    "Are there any urgent deadlines, court hearings, or statutory limitation dates approaching?",
                    "What specific resolution, relief, or legal representation do you expect from a lawyer?"
            );
        };
    }

    @Override
    public String generateCaseSummary(LegalAssistanceSession session, List<LegalAnswer> answers) {
        StringBuilder sb = new StringBuilder();
        LegalCategory category = session.getAiDetectedCategory() != null ?
                session.getAiDetectedCategory() : session.getSelectedCategory();

        sb.append("CASE ASSESSMENT SUMMARY:\n");
        sb.append("• Legal Category: ").append(category != null ? category.getDisplayName() : "General Legal Matter").append("\n");
        sb.append("• Relevant Practice Area: ").append(session.getPracticeArea() != null ? session.getPracticeArea().name().replace("_", " ") : "Civil Disputes").append("\n\n");

        if (session.getInitialProblemDescription() != null && !session.getInitialProblemDescription().isBlank()) {
            sb.append("INITIAL PROBLEM STATEMENT:\n\"").append(session.getInitialProblemDescription().trim()).append("\"\n\n");
        }

        if (answers != null && !answers.isEmpty()) {
            sb.append("FACTS GATHERED FROM CUSTOMER RESPONSES:\n");
            for (int i = 0; i < answers.size(); i++) {
                LegalAnswer ans = answers.get(i);
                String qText = ans.getQuestion() != null ? ans.getQuestion().getQuestionText() : "Question " + (i + 1);
                sb.append((i + 1)).append(". ").append(qText).append("\n");
                sb.append("   → Customer Answer: ").append(ans.getAnswerText()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("LEGAL PREPARATION NOTE:\n");
        sb.append("The above factual background has been structured for consultation with a verified lawyer. Supporting documentation and specific legal strategy should be discussed directly with the matched advocate.\n\n");
        sb.append("DISCLAIMER: ").append(LEGAL_DISCLAIMER);

        return sb.toString();
    }

    @Override
    public PracticeArea mapCategoryToPracticeArea(LegalCategory category) {
        if (category == null) {
            return PracticeArea.CIVIL_DISPUTES;
        }
        return category.getDefaultPracticeArea();
    }
}
