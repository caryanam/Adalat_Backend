package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Campaign entity — fully redesigned for queue-based async processing.
 * Tracks lifecycle (create → queue → process → complete) and aggregate stats.
 */
@Entity
@Table(name = "campaigns",
       indexes = {
           @Index(name = "idx_campaign_client", columnList = "client_id"),
           @Index(name = "idx_campaign_status", columnList = "campaign_status")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WhatsAppTemplate template;

    @Column(name = "template_variables", columnDefinition = "JSON")
    private String templateVariables;

    @Column(name = "header_image_url", columnDefinition = "LONGTEXT")
    private String headerImageUrl;

    @Column(name = "message_limit")
    private Integer messageLimit;

    @Column(name = "delay_between_messages")
    @Builder.Default
    private Integer delayBetweenMessages = 10;

    // ============ Aggregate Stats ============

    @Column(name = "total_recipients")
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(name = "messages_sent")
    @Builder.Default
    private Integer messagesSent = 0;

    @Column(name = "messages_delivered")
    @Builder.Default
    private Integer messagesDelivered = 0;

    @Column(name = "messages_read")
    @Builder.Default
    private Integer messagesRead = 0;

    @Column(name = "messages_failed")
    @Builder.Default
    private Integer messagesFailed = 0;

    @Column(name = "messages_retrying")
    @Builder.Default
    private Integer messagesRetrying = 0;

    // ============ Lifecycle ============

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status", nullable = false)
    @Builder.Default
    private CampaignStatus campaignStatus = CampaignStatus.CREATED;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
