package com.adalat.repository;

import com.adalat.entity.Notification;
import com.adalat.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Specific user notifications
    List<Notification> findByRecipientRoleAndRecipientIdOrderByCreatedAtDesc(Role recipientRole, Long recipientId);

    // Unread count for specific user
    long countByRecipientRoleAndRecipientIdAndIsReadFalse(Role recipientRole, Long recipientId);

    // Role-wide notifications (e.g. for ADMIN)
    @Query("SELECT n FROM Notification n WHERE n.recipientRole = :role AND (n.recipientId = :recipientId OR n.recipientId IS NULL) ORDER BY n.createdAt DESC")
    List<Notification> findUserNotifications(@Param("role") Role role, @Param("recipientId") Long recipientId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientRole = :role AND (n.recipientId = :recipientId OR n.recipientId IS NULL) AND n.isRead = false")
    long countUnreadNotifications(@Param("role") Role role, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.recipientRole = :role AND (n.recipientId = :recipientId OR n.recipientId IS NULL) AND n.isRead = false")
    int markAllAsReadForUser(@Param("role") Role role, @Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
