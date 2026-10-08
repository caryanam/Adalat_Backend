package com.adalat.dto;

import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerPricingRequestDTO {

    private Object consultationRate;
    private Object amount;
    private Integer consultationDuration;
}
