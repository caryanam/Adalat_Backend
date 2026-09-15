package com.adalat.dto.ai;

import com.adalat.enums.IntakeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryDTO {
    private Long sessionId;
    private IntakeStatus status;
    private String primaryCategory;
    private String categoryDisplay;
    private int questionCount;
    private String customerSummarySnippet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean intakeComplete;
    private Long consultationId;
}
