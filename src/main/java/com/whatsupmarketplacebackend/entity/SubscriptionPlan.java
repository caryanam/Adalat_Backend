package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.PlanCode;
import com.whatsupmarketplacebackend.enums.PlanType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType planType;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer messageLimit;

    @Column(nullable = false)
    private Integer campaignLimit;

    @Column(nullable = false)
    private Integer validityDays;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
