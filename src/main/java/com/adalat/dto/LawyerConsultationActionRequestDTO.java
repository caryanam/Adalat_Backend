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
}
