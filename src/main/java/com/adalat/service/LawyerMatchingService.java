package com.adalat.service;

import com.adalat.dto.LawyerSuggestionResponseDTO;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.PracticeArea;

import java.util.List;

public interface LawyerMatchingService {

    List<LawyerSuggestionResponseDTO> matchAndSaveLawyers(LegalAssistanceSession session, PracticeArea practiceArea);

    List<LawyerSuggestionResponseDTO> getSuggestionsForSession(LegalAssistanceSession session);
}
