package com.adalat.dto;

import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import com.adalat.enums.VerificationStatus;
import lombok.*;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerSuggestionResponseDTO {

    private Long lawyerId;
    private String fullName;
    private Integer experience;
    private String location;
    private String education;
    private Set<PracticeArea> practiceAreas;
    private Set<Language> languages;
    private Integer consultationRate;
    private VerificationStatus verificationStatus;
    private Double matchScore;
    private String matchingReason;
}
