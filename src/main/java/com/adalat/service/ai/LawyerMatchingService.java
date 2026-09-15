package com.adalat.service.ai;

import com.adalat.entity.Lawyer;
import com.adalat.enums.*;
import com.adalat.repository.LawyerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LawyerMatchingService {

    private final LawyerRepository lawyerRepository;

    public Optional<Lawyer> findBestMatch(LegalCategory category, String city, String state) {
        // Get all eligible lawyers
        List<Lawyer> eligible = lawyerRepository.findByVerificationStatusAndAccountStatusAndAvailable(
                VerificationStatus.APPROVED, AccountStatus.ACTIVE, true);

        if (eligible.isEmpty()) {
            log.warn("No eligible lawyers found (verified, active, available).");
            return Optional.empty();
        }

        PracticeArea targetArea = (category != null) ? category.getDefaultPracticeArea() : null;
        String cityLower = (city != null) ? city.trim().toLowerCase() : null;

        // Score and rank
        List<Lawyer> ranked = eligible.stream()
                .sorted(Comparator
                        // Practice area match first (matching = 0, not matching = 1)
                        .comparingInt((Lawyer l) -> (targetArea != null && l.getPracticeAreas().contains(targetArea)) ? 0 : 1)
                        // Location match second
                        .thenComparingInt(l -> (cityLower != null && l.getLocation() != null && l.getLocation().toLowerCase().contains(cityLower)) ? 0 : 1)
                        // Higher rating preferred
                        .thenComparing((Lawyer l) -> l.getRating() != null ? l.getRating() : 0.0, Comparator.reverseOrder())
                        // Lower workload preferred
                        .thenComparingInt(l -> l.getTotalConsultations() != null ? l.getTotalConsultations() : 0)
                )
                .collect(Collectors.toList());

        Lawyer best = ranked.get(0);
        log.info("Matched lawyer: id={}, name='{}', practiceAreas={}, location='{}', rating={}",
                best.getLawyerId(), best.getFullName(), best.getPracticeAreas(), best.getLocation(), best.getRating());
        return Optional.of(best);
    }

    public List<Lawyer> findTopMatches(LegalCategory category, String city, int limit) {
        List<Lawyer> eligible = lawyerRepository.findByVerificationStatusAndAccountStatusAndAvailable(
                VerificationStatus.APPROVED, AccountStatus.ACTIVE, true);

        if (eligible.isEmpty()) {
            return List.of();
        }

        PracticeArea targetArea = (category != null) ? category.getDefaultPracticeArea() : null;
        String cityLower = (city != null) ? city.trim().toLowerCase() : null;

        return eligible.stream()
                .sorted(Comparator
                        .comparingInt((Lawyer l) -> (targetArea != null && l.getPracticeAreas().contains(targetArea)) ? 0 : 1)
                        .thenComparingInt(l -> (cityLower != null && l.getLocation() != null && l.getLocation().toLowerCase().contains(cityLower)) ? 0 : 1)
                        .thenComparing((Lawyer l) -> l.getRating() != null ? l.getRating() : 0.0, Comparator.reverseOrder())
                        .thenComparingInt(l -> l.getTotalConsultations() != null ? l.getTotalConsultations() : 0)
                )
                .limit(limit)
                .collect(Collectors.toList());
    }
}
