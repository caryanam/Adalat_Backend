package com.adalat.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallEndDTO {
    private Long callId;
    private Long consultationRequestId;
    private String reason;
}
