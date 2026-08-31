package com.adalat.dto;

import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerLoginResponseDTO {

    private String token;
    private LawyerProfileResponseDTO lawyer;
}
