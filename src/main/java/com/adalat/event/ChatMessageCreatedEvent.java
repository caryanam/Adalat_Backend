package com.adalat.event;

import com.adalat.dto.ConsultationChatMessageDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChatMessageCreatedEvent extends ApplicationEvent {
    private final Long requestId;
    private final ConsultationChatMessageDTO message;

    public ChatMessageCreatedEvent(Object source, Long requestId, ConsultationChatMessageDTO message) {
        super(source);
        this.requestId = requestId;
        this.message = message;
    }
}
