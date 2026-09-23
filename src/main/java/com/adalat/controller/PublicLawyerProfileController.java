package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.entity.Lawyer;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.LawyerRepository;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.LawyerRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lawyers")
@RequiredArgsConstructor
@Tag(name = "Public Lawyer Profiles", description = "Public endpoints to view verified advocate profiles, ratings, and reviews")
public class PublicLawyerProfileController {

    private final LawyerRepository lawyerRepository;
    private final LawyerRatingService lawyerRatingService;

    @GetMapping("/{lawyerId}")
    @Operation(summary = "View advocate public profile", description = "Returns public professional info, experience, ratings, and rates")
    public ResponseEntity<ApiResponseDTO<PublicLawyerProfileDTO>> getPublicProfile(@PathVariable Long lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .filter(l -> l.getVerificationStatus() == com.adalat.enums.VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Verified advocate not found with ID: " + lawyerId));

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
                .consultationFee(lawyer.getConsultationFee() != null ? lawyer.getConsultationFee() : 99)
                .verificationStatus(lawyer.getVerificationStatus())
                .rating(lawyer.getRating() != null ? lawyer.getRating() : 0.0)
                .ratingCount(lawyer.getRatingCount() != null ? lawyer.getRatingCount() : 0)
                .totalConsultations(lawyer.getTotalConsultations() != null ? lawyer.getTotalConsultations() : 0)
                .available(lawyer.getAvailable() != null ? lawyer.getAvailable() : true)
                .profilePhotoUrl(lawyer.getProfilePhotoUrl())
                .build();

        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Advocate profile fetched successfully.", profileDTO));
    }

    @GetMapping("/{lawyerId}/ratings")
    @Operation(summary = "Get advocate reviews & ratings", description = "Fetch dynamic rating summary and client reviews from database for an advocate")
    public ResponseEntity<ApiResponseDTO<LawyerRatingSummaryDTO>> getRatings(@PathVariable Long lawyerId) {
        LawyerRatingSummaryDTO summary = lawyerRatingService.getLawyerRatings(lawyerId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Advocate ratings fetched successfully.", summary));
    }

    @PostMapping("/{lawyerId}/ratings")
    @Operation(summary = "Submit rating for an advocate", description = "Directly submit rating (1-5) and review for an advocate")
    public ResponseEntity<ApiResponseDTO<LawyerReviewResponseDTO>> submitRating(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long lawyerId,
            @Valid @RequestBody LawyerReviewRequestDTO requestDTO) {

        Long customerId = userDetails != null ? userDetails.getId() : null;
        LawyerReviewResponseDTO review = lawyerRatingService.submitRating(lawyerId, customerId, requestDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Rating submitted successfully.", review));
    }
}
