package com.adalat.dto;

import com.adalat.enums.ConsultationRate;
import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import com.adalat.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicLawyerProfileDTO {

    private Long lawyerId;
    private String fullName;
    private String bio;
    private Integer yearsOfExperience;
    private Set<PracticeArea> practiceAreas;
    private Set<Language> languages;
    private String location;
    private String education;
    private String barEnrollmentNumber;
    private ConsultationRate consultationRate;
    private Integer consultationRateAmount;
    private Integer consultationFee;
    private VerificationStatus verificationStatus;
    private Double rating;
    private Integer ratingCount;
    private Integer totalConsultations;
    private Boolean available;
    private String profilePhotoUrl;
}
