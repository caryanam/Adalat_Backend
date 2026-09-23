package com.adalat.dto;

import com.adalat.enums.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerProfileResponseDTO {

    private Long lawyerId;
    private String fullName;
    private String email;
    private String mobileNumber;

    // Professional
    private String barEnrollmentNumber;
    private Integer yearsOfExperience;
    private String education;
    private String location;
    private Set<PracticeArea> practiceAreas;
    private Set<Language> languages;
    private String bio;

    // Pricing & UPI
    private ConsultationRate consultationRate;
    private Integer consultationRateAmount;
    private Integer consultationFee;
    private String upiId;

    // Status
    private Role role;
    private RegistrationStatus registrationStatus;
    private VerificationStatus verificationStatus;
    private AccountStatus accountStatus;
    private Boolean available;
    private Double rating;
    private Integer ratingCount;
    private Integer totalConsultations;
    private String profilePhotoUrl;
    private String rejectionReason;

    // Documents
    private List<LawyerDocumentResponseDTO> documents;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
