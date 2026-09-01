package com.adalat.serviceImpl;

import com.adalat.dto.LawyerSuggestionResponseDTO;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerSuggestion;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.AccountStatus;
import com.adalat.enums.PracticeArea;
import com.adalat.enums.Role;
import com.adalat.enums.VerificationStatus;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.LawyerSuggestionRepository;
import com.adalat.service.LawyerMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawyerMatchingServiceImpl implements LawyerMatchingService {

    private final LawyerRepository lawyerRepository;
    private final LawyerSuggestionRepository lawyerSuggestionRepository;

    @Override
    @Transactional
    public List<LawyerSuggestionResponseDTO> matchAndSaveLawyers(LegalAssistanceSession session, PracticeArea practiceArea) {
        // Fetch all verified & active lawyers
        List<Lawyer> allApprovedLawyers = lawyerRepository.findByVerificationStatus(VerificationStatus.APPROVED)
                .stream()
                .filter(l -> l.getAccountStatus() == AccountStatus.ACTIVE && l.getRole() == Role.LAWYER)
                .toList();

        List<LawyerSuggestionResponseDTO> suggestions = new ArrayList<>();

        for (Lawyer lawyer : allApprovedLawyers) {
            boolean exactPracticeMatch = practiceArea != null && lawyer.getPracticeAreas() != null && lawyer.getPracticeAreas().contains(practiceArea);
            double baseScore = exactPracticeMatch ? 0.90 : 0.70;
            int exp = lawyer.getYearsOfExperience() != null ? lawyer.getYearsOfExperience() : 1;
            double expBonus = Math.min(exp * 0.01, 0.08);
            double finalScore = Math.round((baseScore + expBonus) * 100.0) / 100.0;

            String reason = exactPracticeMatch
                    ? "Verified specialist in " + practiceArea.name().replace("_", " ") + " with " + exp + " years of experience."
                    : "Verified high-rated advocate with " + exp + " years of general legal experience.";

            // Save suggestion record in DB if not already present
            if (lawyerSuggestionRepository.findByLegalSessionAndLawyer(session, lawyer).isEmpty()) {
                LawyerSuggestion suggestion = LawyerSuggestion.builder()
                        .legalSession(session)
                        .lawyer(lawyer)
                        .matchScore(finalScore)
                        .matchingReason(reason)
                        .build();
                lawyerSuggestionRepository.save(suggestion);
            }

            suggestions.add(LawyerSuggestionResponseDTO.builder()
                    .lawyerId(lawyer.getLawyerId())
                    .fullName(lawyer.getFullName())
                    .experience(lawyer.getYearsOfExperience())
                    .location(lawyer.getLocation())
                    .education(lawyer.getEducation())
                    .practiceAreas(lawyer.getPracticeAreas())
                    .languages(lawyer.getLanguages())
                    .consultationRate(lawyer.getConsultationRate() != null ? lawyer.getConsultationRate().getAmount() : 99)
                    .verificationStatus(lawyer.getVerificationStatus())
                    .bio(lawyer.getBio())
                    .rating(lawyer.getRating() != null ? lawyer.getRating() : 4.8)
                    .available(lawyer.getAvailable() != null ? lawyer.getAvailable() : true)
                    .totalConsultations(lawyer.getTotalConsultations() != null ? lawyer.getTotalConsultations() : 0)
                    .profilePhotoUrl(lawyer.getProfilePhotoUrl())
                    .matchScore(finalScore)
                    .matchingReason(reason)
                    .build());
        }

        // Sort by match score descending
        suggestions.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        log.info("Matched {} verified lawyers for session id={}", suggestions.size(), session.getId());
        return suggestions;
    }

    @Override
    public List<LawyerSuggestionResponseDTO> getSuggestionsForSession(LegalAssistanceSession session) {
        return lawyerSuggestionRepository.findByLegalSession(session).stream()
                .map(s -> {
                    Lawyer l = s.getLawyer();
                    return LawyerSuggestionResponseDTO.builder()
                            .lawyerId(l.getLawyerId())
                            .fullName(l.getFullName())
                            .experience(l.getYearsOfExperience())
                            .location(l.getLocation())
                            .education(l.getEducation())
                            .practiceAreas(l.getPracticeAreas())
                            .languages(l.getLanguages())
                            .consultationRate(l.getConsultationRate() != null ? l.getConsultationRate().getAmount() : 99)
                            .verificationStatus(l.getVerificationStatus())
                            .bio(l.getBio())
                            .rating(l.getRating() != null ? l.getRating() : 4.8)
                            .available(l.getAvailable() != null ? l.getAvailable() : true)
                            .totalConsultations(l.getTotalConsultations() != null ? l.getTotalConsultations() : 0)
                            .profilePhotoUrl(l.getProfilePhotoUrl())
                            .matchScore(s.getMatchScore())
                            .matchingReason(s.getMatchingReason())
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .collect(Collectors.toList());
    }
}
