package com.adalat.dto;

import com.adalat.enums.MessageType;
import com.adalat.enums.SenderType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalChatMessageResponseDTO {

    private Long id;
    private Long sessionId;
    private SenderType senderType;
    private String message;
    private MessageType messageType;
    private LocalDateTime createdAt;
}
