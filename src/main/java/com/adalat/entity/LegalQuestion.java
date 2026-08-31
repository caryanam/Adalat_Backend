package com.adalat.entity;

import com.adalat.enums.LegalCategory;
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
@Table(name = "legal_questions")
public class LegalQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LegalAssistanceSession session;

    @Column(nullable = false)
    private Integer questionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalCategory category;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
