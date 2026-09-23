package com.adalat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawyerRatingSummaryDTO {
    private Long lawyerId;
    private Double averageRating;
    private Integer ratingCount;
    private List<LawyerReviewResponseDTO> reviews;
}
