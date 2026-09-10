package com.adalat.dto;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateConsultationRequestDTO {

    @NotNull(message = "Lawyer ID is required")
    private Long lawyerId;

    private LegalCategory category;

    private PracticeArea practiceArea;

    private String caseSummary;

    private LocalDateTime scheduledAt;
}
