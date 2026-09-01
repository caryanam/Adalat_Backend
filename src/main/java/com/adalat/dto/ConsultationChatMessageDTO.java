package com.adalat.dto;

import com.adalat.enums.ChatMessageStatus;
import com.adalat.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultationChatMessageDTO {

    private Long id;
    private Long consultationRequestId;
    private Long senderId;
    private SenderType senderType;
    private String senderName;
    private String message;
    private ChatMessageStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime seenAt;
}
