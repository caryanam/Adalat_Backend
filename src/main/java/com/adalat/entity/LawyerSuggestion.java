package com.adalat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lawyer_suggestions")
public class LawyerSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_session_id", nullable = false)
    private LegalAssistanceSession legalSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private Lawyer lawyer;

    private Double matchScore;

    private String matchingReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
