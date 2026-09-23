package com.adalat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class ChatMessageSeenEvent extends ApplicationEvent {
    private final Long requestId;
    private final List<Long> messageIds;

    public ChatMessageSeenEvent(Object source, Long requestId, List<Long> messageIds) {
        super(source);
        this.requestId = requestId;
        this.messageIds = messageIds;
    }
}
