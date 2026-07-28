package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private ClientSubscription subscription;

    @Column(nullable = false)
    private String campaignName;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "header_image_url", columnDefinition = "LONGTEXT")
    private String headerImageUrl;

    @Column(name = "template_values_json", columnDefinition = "LONGTEXT")
    private String templateValuesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus campaignStatus;

    private Integer messagesSent = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
