package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.TemplateCategory;
import com.whatsupmarketplacebackend.enums.TemplateHeaderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * WhatsApp message template synced from Meta Business API.
 * Templates are NOT hardcoded — they are fetched via /templates/sync endpoint.
 */
@Entity
@Table(name = "whatsapp_templates",
       indexes = {
           @Index(name = "idx_meta_template_id", columnList = "meta_template_id"),
           @Index(name = "idx_template_status", columnList = "status")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateCategory category;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 20)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_type", nullable = false)
    @Builder.Default
    private TemplateHeaderType headerType = TemplateHeaderType.NONE;

    @Column(name = "header_text", columnDefinition = "TEXT")
    private String headerText;

    @Column(name = "header_variables", columnDefinition = "JSON")
    private String headerVariables;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(columnDefinition = "TEXT")
    private String footer;

    @Column(columnDefinition = "JSON")
    private String buttons;

    @Column(name = "variable_count")
    @Builder.Default
    private Integer variableCount = 0;

    @Column(name = "meta_template_id", unique = true)
    private String metaTemplateId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
