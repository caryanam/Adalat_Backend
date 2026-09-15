package com.adalat.dto.ai;

import com.adalat.enums.IntakeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalIntakeResponseDTO {
    private Long sessionId;
    private IntakeStatus status;
    private String assistantMessage;
    private String primaryCategory;
    private List<String> secondaryCategories;
    private JurisdictionDTO jurisdiction;
    private String urgency;
    private boolean intakeComplete;
    private String customerSummary;
    private boolean canConfirm;
    private boolean canEdit;
    private int questionCount;
    private List<LegalIntakeMessageDTO> messageHistory;

    // Workflow state fields
    private boolean summaryConfirmed;
    private boolean nextActionRequired;
    private List<String> availableActions;
    private Long consultationId;
    private LawyerInfoDTO matchedLawyer;
    private List<LawyerInfoDTO> suggestedLawyers;
}
