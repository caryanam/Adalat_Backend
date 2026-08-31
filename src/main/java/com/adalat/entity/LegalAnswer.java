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
@Table(name = "legal_answers")
public class LegalAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LegalAssistanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private LegalQuestion question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
