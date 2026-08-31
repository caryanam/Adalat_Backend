package com.adalat.dto;

import com.adalat.enums.VerificationStatus;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerSubmitResponseDTO {

    private Long lawyerId;
    private String message;
    private VerificationStatus verificationStatus;
}
