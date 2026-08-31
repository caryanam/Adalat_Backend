package com.adalat.service;

import com.adalat.dto.LegalCategoryResultResponseDTO;
import com.adalat.entity.LegalAnswer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;

import java.util.List;

public interface AiLegalAssistantService {

    LegalCategoryResultResponseDTO detectCategoryFromText(String problemText, Long sessionId);

    List<String> getQuestionsForCategory(LegalCategory category);

    String generateCaseSummary(LegalAssistanceSession session, List<LegalAnswer> answers);

    PracticeArea mapCategoryToPracticeArea(LegalCategory category);
}
