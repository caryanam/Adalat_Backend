package com.adalat.serviceImpl;

import com.adalat.dto.NotificationDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Notification;
import com.adalat.enums.NotificationType;
import com.adalat.enums.Role;
import com.adalat.event.NotificationCreatedEvent;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.NotificationRepository;
import com.adalat.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public NotificationDTO createNotification(
            Role recipientRole,
            Long recipientId,
            String title,
            String message,
            NotificationType type,
            Long referenceId,
            String referenceType,
            String link
    ) {
        Notification notification = Notification.builder()
                .recipientRole(recipientRole)
                .recipientId(recipientId)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .link(link)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Saved notification in DB: id={}, role={}, recipientId={}, type={}",
                saved.getId(), recipientRole, recipientId, type);

        NotificationDTO dto = toDTO(saved);

        // Publish event for real-time socket emission
        try {
            eventPublisher.publishEvent(new NotificationCreatedEvent(this, dto));
        } catch (Exception e) {
            log.error("Failed to publish NotificationCreatedEvent: {}", e.getMessage());
        }

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsForUser(Long userId, Role role) {
        List<Notification> list = notificationRepository.findUserNotifications(role, userId);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId, Role role) {
        return notificationRepository.countUnreadNotifications(role, userId);
    }

    @Override
    @Transactional
    public NotificationDTO markAsRead(Long notificationId, Long userId, Role role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        // Validate access
        if (role != Role.ADMIN && notification.getRecipientId() != null && !notification.getRecipientId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to access this notification.");
        }

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId, Role role) {
        notificationRepository.markAllAsReadForUser(role, userId, LocalDateTime.now());
        log.info("Marked all notifications as read for role={}, userId={}", role, userId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long userId, Role role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (role != Role.ADMIN && notification.getRecipientId() != null && !notification.getRecipientId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to delete this notification.");
        }

        notificationRepository.delete(notification);
        log.info("Deleted notification: id={}, by userId={}", notificationId, userId);
    }

    @Override
    @Transactional
    public void notifyCustomerLawyerWaiting(Long lawyerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        String lawyerName = request.getLawyer() != null ? request.getLawyer().getFullName() : "Your Advocate";
        Long customerId = request.getCustomer() != null ? request.getCustomer().getCustomerId() : null;

        if (customerId != null) {
            createNotification(
                    Role.CUSTOMER,
                    customerId,
                    "Advocate is Waiting in Consultation Room",
                    "Adv. " + lawyerName + " is online and waiting for you in the consultation chat room. Please join now!",
                    NotificationType.LAWYER_WAITING,
                    requestId,
                    "CONSULTATION",
                    "/customer/chat/" + requestId
            );
            log.info("Sent lawyer-waiting notification to customerId={} for requestId={}", customerId, requestId);
        }
    }

    private NotificationDTO toDTO(Notification entity) {
        return NotificationDTO.builder()
                .id(entity.getId())
                .recipientRole(entity.getRecipientRole())
                .recipientId(entity.getRecipientId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .referenceId(entity.getReferenceId())
                .referenceType(entity.getReferenceType())
                .link(entity.getLink())
                .isRead(entity.isRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
