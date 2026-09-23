package com.adalat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class ChatMessageDeliveredEvent extends ApplicationEvent {
    private final Long requestId;
    private final List<Long> messageIds;

    public ChatMessageDeliveredEvent(Object source, Long requestId, List<Long> messageIds) {
        super(source);
        this.requestId = requestId;
        this.messageIds = messageIds;
    }
}
