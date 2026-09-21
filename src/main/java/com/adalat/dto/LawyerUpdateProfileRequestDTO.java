package com.adalat.dto;

import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerUpdateProfileRequestDTO {

    private String fullName;

    private String mobileNumber;

    private String barEnrollmentNumber;

    private Integer yearsOfExperience;

    private String education;

    private String location;

    private Set<PracticeArea> practiceAreas;

    private Set<Language> languages;

    private String bio;

    private Integer consultationFee;

    private String upiId;

    private String profilePhotoUrl;
}
