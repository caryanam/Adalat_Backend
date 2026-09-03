package com.adalat.dto;

import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Set;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerProfessionalRequestDTO {

    @NotBlank(message = "Bar enrollment number is required")
    private String barEnrollmentNumber;

    @NotNull(message = "Years of experience is required")
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 60, message = "Years of experience seems too high")
    private Integer yearsOfExperience;

    @NotBlank(message = "Education details are required")
    private String education;

    @NotBlank(message = "Location is required")
    private String location;

    @NotEmpty(message = "Please select at least one practice area")
    private Set<PracticeArea> practiceAreas;

    @NotEmpty(message = "Please select at least one language")
    private Set<Language> languages;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    private String profilePhotoUrl;
}
