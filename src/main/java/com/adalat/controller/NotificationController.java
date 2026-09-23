package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.NotificationDTO;
import com.adalat.enums.Role;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Endpoints for Customer, Lawyer, and Admin notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Get user notifications", description = "Fetches all notifications for the authenticated user, sorted by date descending")
    public ResponseEntity<ApiResponseDTO<List<NotificationDTO>>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        Role role = userDetails != null ? userDetails.getRole() : Role.CUSTOMER;

        List<NotificationDTO> list = notificationService.getNotificationsForUser(userId, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Notifications retrieved successfully.", list));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Get unread count", description = "Fetches the total number of unread notifications for badge display")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        Role role = userDetails != null ? userDetails.getRole() : Role.CUSTOMER;

        long unreadCount = notificationService.getUnreadCount(userId, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Unread count retrieved.", Map.of("unreadCount", unreadCount)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Mark single notification as read", description = "Updates isRead=true for a specific notification")
    public ResponseEntity<ApiResponseDTO<NotificationDTO>> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        Role role = userDetails != null ? userDetails.getRole() : Role.CUSTOMER;

        NotificationDTO updated = notificationService.markAsRead(id, userId, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Notification marked as read.", updated));
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications for user as read")
    public ResponseEntity<ApiResponseDTO<String>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        Role role = userDetails != null ? userDetails.getRole() : Role.CUSTOMER;

        notificationService.markAllAsRead(userId, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "All notifications marked as read.", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Delete notification", description = "Deletes a specific notification from database")
    public ResponseEntity<ApiResponseDTO<String>> deleteNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        Role role = userDetails != null ? userDetails.getRole() : Role.CUSTOMER;

        notificationService.deleteNotification(id, userId, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Notification deleted.", null));
    }

    @PostMapping("/notify-waiting/{requestId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    @Operation(summary = "Notify customer that lawyer is waiting", description = "Pushes an urgent notification to the customer that the advocate is waiting in the chat room")
    public ResponseEntity<ApiResponseDTO<String>> notifyWaiting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) {

        Long lawyerId = userDetails != null ? userDetails.getId() : 1L;
        notificationService.notifyCustomerLawyerWaiting(lawyerId, requestId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Client notified that you are waiting in the room.", null));
    }
}
