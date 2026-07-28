package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.response.NotificationResponseDTO;
import com.whatsupmarketplacebackend.entity.Admin;
import com.whatsupmarketplacebackend.entity.Notification;
import com.whatsupmarketplacebackend.enums.NotificationType;
import com.whatsupmarketplacebackend.repository.AdminRepository;
import com.whatsupmarketplacebackend.repository.NotificationRepository;
import com.whatsupmarketplacebackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AdminRepository adminRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public void sendNotification(String email, String title, String message, NotificationType type) {
        Notification notification = new Notification();
        notification.setRecipientEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setIsRead(false);
        
        notificationRepository.save(notification);
        log.info("Notification sent to: {}", email);
    }

    @Override
    @Transactional
    public void sendToAllAdmins(String title, String message, NotificationType type) {
        List<Admin> admins = adminRepository.findAll();
        
        List<Notification> notifications = admins.stream().map(admin -> {
            Notification notification = new Notification();
            notification.setRecipientEmail(admin.getEmail());
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setIsRead(false);
            return notification;
        }).collect(Collectors.toList());
        
        notificationRepository.saveAll(notifications);
        log.info("Notification sent to all admins.");
    }

    @Override
    public List<NotificationResponseDTO> getUserNotifications(String email, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly 
                ? notificationRepository.findByRecipientEmailAndIsReadFalseOrderByCreatedAtDesc(email)
                : notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email);
                
        return notifications.stream()
                .map(n -> modelMapper.map(n, NotificationResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(String email) {
        return notificationRepository.countByRecipientEmailAndIsReadFalse(email);
    }

    @Override
    @Transactional
    public void markAsRead(List<Long> notificationIds, String email) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        
        notifications.stream()
                .filter(n -> n.getRecipientEmail().equals(email)) // Ensure they own it
                .forEach(n -> n.setIsRead(true));
                
        notificationRepository.saveAll(notifications);
    }

    @Override
    @Transactional
    public void markAllAsRead(String email) {
        List<Notification> notifications = notificationRepository.findByRecipientEmailAndIsReadFalseOrderByCreatedAtDesc(email);
        notifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(notifications);
    }
}
