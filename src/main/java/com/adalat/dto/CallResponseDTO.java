package com.adalat.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallResponseDTO {
    private Long callId;
    private Long consultationRequestId;
    private String response;
}
