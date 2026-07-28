package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.response.NotificationResponseDTO;
import com.whatsupmarketplacebackend.enums.NotificationType;

import java.util.List;

public interface NotificationService {

    void sendNotification(String email, String title, String message, NotificationType type);
    
    void sendToAllAdmins(String title, String message, NotificationType type);
    
    List<NotificationResponseDTO> getUserNotifications(String email, boolean unreadOnly);
    
    long getUnreadCount(String email);
    
    void markAsRead(List<Long> notificationIds, String email);
    
    void markAllAsRead(String email);
}
