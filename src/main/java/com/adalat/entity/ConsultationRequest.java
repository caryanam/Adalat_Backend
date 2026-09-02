package com.adalat.entity;

import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.LegalCategory;
import com.adalat.enums.PracticeArea;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consultation_requests")
public class ConsultationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_session_id", nullable = true)
    private LegalAssistanceSession legalSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private Lawyer lawyer;

    @Enumerated(EnumType.STRING)
    private LegalCategory category;

    @Enumerated(EnumType.STRING)
    private PracticeArea practiceArea;

    @Column(columnDefinition = "TEXT")
    private String caseSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConsultationRequestStatus status = ConsultationRequestStatus.REQUESTED;

    @Column(columnDefinition = "TEXT")
    private String lawyerNotes;

    private LocalDateTime scheduledAt;

    private BigDecimal paymentAmount;

    private String assignedDate;

    private String assignedTime;

    private String customerConfirmationStatus;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
