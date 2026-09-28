package com.adalat.dto;

import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Set;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerProfessionalRequestDTO {

    @NotBlank(message = "Bar Council Enrollment Number is required")
    @Size(min = 5, max = 50, message = "Bar Council Enrollment Number is invalid. A single letter or incomplete number is not accepted")
    @Pattern(
        regexp = "^[A-Za-z]{1,5}/\\d{1,6}/\\d{4}$",
        message = "Invalid Bar Council Enrollment format. Expected format: STATE/NUM/YEAR (e.g., MAH/1234/2020 or D/456/2018)"
    )
    private String barEnrollmentNumber;

    @NotNull(message = "Years of experience is required")
    @Min(value = 0, message = "Years of experience cannot be negative. Must be 0 or a positive number")
    @Max(value = 70, message = "Years of experience cannot exceed 70 years")
    private Integer yearsOfExperience;

    @NotBlank(message = "Education details are required")
    @Size(min = 2, max = 200, message = "Education / Qualifications must be at least 2 characters long (e.g., LL.B., B.A. LL.B., LL.M.). A single letter is not accepted")
    private String education;

    @NotBlank(message = "Location / Court City is required")
    @Size(min = 2, max = 100, message = "Location / Court City must be at least 2 characters long. A single letter is not accepted")
    @Pattern(
        regexp = "^[a-zA-Z\\s.-]+$",
        message = "Location / Court City can only contain alphabets, spaces, and hyphens. Numbers and special characters are not allowed"
    )
    private String location;

    @NotEmpty(message = "Please select at least one practice area")
    private Set<PracticeArea> practiceAreas;

    @NotEmpty(message = "Please select at least one language")
    private Set<Language> languages;

    @NotBlank(message = "Professional Bio & Practice Summary is required")
    @Size(min = 50, max = 2000, message = "Professional Bio must be at least 50 characters long to provide meaningful detail. A single letter or brief text is not accepted")
    private String bio;

    private String profilePhotoUrl;
}
