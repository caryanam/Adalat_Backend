package com.adalat.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LawyerConsultationActionRequestDTO {

    private String assignedDate;
    private String assignedTime;
    private String notes;
    private String reason;

    public String getEffectiveNotes() {
        if (notes != null && !notes.isBlank()) {
            return notes.trim();
        }
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return null;
    }
}
