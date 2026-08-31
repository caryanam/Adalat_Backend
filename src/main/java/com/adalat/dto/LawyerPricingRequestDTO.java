package com.adalat.dto;

import com.adalat.enums.ConsultationRate;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerPricingRequestDTO {

    @NotNull(message = "Consultation rate is required")
    private ConsultationRate consultationRate;
}
