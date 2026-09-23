package com.adalat.service;

import com.adalat.dto.LawyerRatingSummaryDTO;
import com.adalat.dto.LawyerReviewRequestDTO;
import com.adalat.dto.LawyerReviewResponseDTO;

public interface LawyerRatingService {
    LawyerReviewResponseDTO submitRating(Long lawyerId, Long customerId, LawyerReviewRequestDTO requestDTO);
    LawyerReviewResponseDTO submitConsultationRating(Long customerId, Long consultationId, LawyerReviewRequestDTO requestDTO);
    LawyerRatingSummaryDTO getLawyerRatings(Long lawyerId);
}
