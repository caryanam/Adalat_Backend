package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.VariableType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Maps template variable placeholders ({{1}}, {{2}}, etc.) to either
 * dynamic customer data fields or static values for a specific campaign.
 */
@Entity
@Table(name = "campaign_variable_mappings",
       indexes = {
           @Index(name = "idx_cvm_campaign", columnList = "campaign_id")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignVariableMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "variable_index", nullable = false)
    private Integer variableIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "variable_type", nullable = false)
    private VariableType variableType;

    /**
     * For DYNAMIC: the customer data field name (e.g., "customer_name", "city", "business_name")
     * For STATIC: not used (null)
     */
    @Column(name = "field_name")
    private String fieldName;

    /**
     * For STATIC: the literal value to use for all recipients
     * For DYNAMIC: not used (null)
     */
    @Column(name = "static_value", columnDefinition = "TEXT")
    private String staticValue;
}
