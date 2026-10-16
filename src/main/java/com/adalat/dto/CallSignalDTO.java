package com.adalat.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignalDTO {
    private Long callId;
    private Long consultationRequestId;
    private Long targetUserId;
    private String targetRole;
    private String type;
    private Object data;
}
