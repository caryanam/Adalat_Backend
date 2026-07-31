package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.WebhookEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores raw webhook payloads from Meta WhatsApp Cloud API for auditing and debugging.
 */
@Entity
@Table(name = "webhook_logs",
       indexes = {
           @Index(name = "idx_wl_whatsapp_msg_id", columnList = "whatsapp_message_id")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whatsapp_message_id")
    private String whatsappMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private WebhookEventType eventType;

    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "recipient_phone", length = 15)
    private String recipientPhone;

    @Column(name = "event_timestamp")
    private LocalDateTime eventTimestamp;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
