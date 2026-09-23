package com.adalat.event;

import com.adalat.dto.NotificationDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationCreatedEvent extends ApplicationEvent {
    private final NotificationDTO notification;

    public NotificationCreatedEvent(Object source, NotificationDTO notification) {
        super(source);
        this.notification = notification;
    }
}
