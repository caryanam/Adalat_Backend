package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Campaign recipient — each row represents one message to one customer.
 * This table doubles as the DB-backed queue:
 *   PENDING → worker picks up → SENDING → SENT/FAILED/RETRY
 */
@Entity
@Table(name = "campaign_recipients",
       indexes = {
           @Index(name = "idx_cr_campaign_status", columnList = "campaign_id, message_status"),
           @Index(name = "idx_cr_whatsapp_msg_id", columnList = "whatsapp_message_id"),
           @Index(name = "idx_cr_campaign_pending", columnList = "campaign_id, message_status, id")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerData customer;

    @Column(name = "recipient_phone", nullable = false, length = 15)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false)
    @Builder.Default
    private MessageStatus messageStatus = MessageStatus.PENDING;

    @Column(name = "whatsapp_message_id")
    private String whatsappMessageId;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
