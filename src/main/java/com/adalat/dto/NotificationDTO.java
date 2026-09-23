package com.adalat.dto;

import com.adalat.enums.NotificationType;
import com.adalat.enums.Role;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Role recipientRole;
    private Long recipientId;
    private String title;
    private String message;
    private NotificationType type;
    private Long referenceId;
    private String referenceType;
    private String link;
    private boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
