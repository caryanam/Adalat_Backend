package com.adalat.service;

import com.adalat.dto.NotificationDTO;
import com.adalat.enums.NotificationType;
import com.adalat.enums.Role;

import java.util.List;

public interface NotificationService {

    NotificationDTO createNotification(
            Role recipientRole,
            Long recipientId,
            String title,
            String message,
            NotificationType type,
            Long referenceId,
            String referenceType,
            String link
    );

    List<NotificationDTO> getNotificationsForUser(Long userId, Role role);

    long getUnreadCount(Long userId, Role role);

    NotificationDTO markAsRead(Long notificationId, Long userId, Role role);

    void markAllAsRead(Long userId, Role role);

    void deleteNotification(Long notificationId, Long userId, Role role);

    void notifyCustomerLawyerWaiting(Long lawyerId, Long requestId);
}
