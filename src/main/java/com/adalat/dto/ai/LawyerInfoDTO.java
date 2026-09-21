package com.adalat.dto.ai;

import com.adalat.enums.PracticeArea;
import com.adalat.enums.Language;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class LawyerInfoDTO {
    private Long lawyerId;
    private String fullName;
    private String location;
    private Set<PracticeArea> practiceAreas;
    private Set<Language> languages;
    private Double rating;
    private Integer consultationFee;
    private Integer yearsOfExperience;
    private String bio;
    private String barEnrollmentNumber;
    private String profilePhotoUrl;
    private String education;
    private Boolean available;
    private Integer totalConsultations;
}
