package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.PublicLawyerProfileDTO;
import com.adalat.entity.Lawyer;
import com.adalat.enums.VerificationStatus;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.LawyerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lawyers")
@RequiredArgsConstructor
@Tag(name = "Public Lawyer Profiles", description = "Public endpoints to view verified advocate profiles")
public class PublicLawyerProfileController {

    private final LawyerRepository lawyerRepository;

    @GetMapping("/{lawyerId}")
    @Operation(summary = "View advocate public profile", description = "Returns public professional info, experience, ratings, and rates")
    public ResponseEntity<ApiResponseDTO<PublicLawyerProfileDTO>> getPublicProfile(@PathVariable Long lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        PublicLawyerProfileDTO profileDTO = PublicLawyerProfileDTO.builder()
                .lawyerId(lawyer.getLawyerId())
                .fullName(lawyer.getFullName())
                .bio(lawyer.getBio())
                .yearsOfExperience(lawyer.getYearsOfExperience())
                .practiceAreas(lawyer.getPracticeAreas())
                .languages(lawyer.getLanguages())
                .location(lawyer.getLocation())
                .education(lawyer.getEducation())
                .barEnrollmentNumber(lawyer.getBarEnrollmentNumber())
                .consultationRate(lawyer.getConsultationRate())
                .consultationRateAmount(lawyer.getConsultationRate() != null ? lawyer.getConsultationRate().getAmount() : 99)
                .verificationStatus(lawyer.getVerificationStatus())
                .rating(lawyer.getRating() != null ? lawyer.getRating() : 4.8)
                .totalConsultations(lawyer.getTotalConsultations() != null ? lawyer.getTotalConsultations() : 0)
                .available(lawyer.getAvailable() != null ? lawyer.getAvailable() : true)
                .profilePhotoUrl(lawyer.getProfilePhotoUrl())
                .build();

        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Advocate profile fetched successfully.", profileDTO));
    }
}
