package com.adalat.entity;

import com.adalat.enums.IntakeStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.util.HashMapConverter;
import com.adalat.util.StringSetConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "legal_intake_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalIntakeSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IntakeStatus status = IntakeStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    private LegalCategory primaryCategory;

    @Convert(converter = HashMapConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, Object> collectedFacts = new HashMap<>();

    @Convert(converter = StringSetConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private Set<String> askedFactKeys = new HashSet<>();

    @Convert(converter = com.adalat.util.ListStringConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private java.util.List<String> askedQuestions = new java.util.ArrayList<>();

    @Version
    private Long version;

    private String jurisdictionState;
    private String jurisdictionCity;
    private String urgency;
    private String lastAskedFactKey;

    @Builder.Default
    private int questionCount = 0;
    
    @Builder.Default
    private boolean intakeComplete = false;
    
    @Builder.Default
    private boolean confirmed = false;

    @Column(columnDefinition = "TEXT")
    private String customerSummary;

    @Column(columnDefinition = "TEXT")
    private String lawyerSummary;

    private Long assignedConsultationRequestId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
