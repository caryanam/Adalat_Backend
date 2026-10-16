package com.adalat.dto;

import com.adalat.enums.CallStatus;
import com.adalat.enums.CallType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationCallDTO {
    private Long id;
    private Long consultationRequestId;
    private Long callerId;
    private String callerRole;
    private String callerName;
    private Long receiverId;
    private String receiverRole;
    private String receiverName;
    private CallType callType;
    private CallStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private String endReason;
    private LocalDateTime createdAt;
}
