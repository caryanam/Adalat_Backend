package com.adalat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawyerReviewResponseDTO {
    private Long id;
    private Long lawyerId;
    private Long customerId;
    private String customerName;
    private Long consultationRequestId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
