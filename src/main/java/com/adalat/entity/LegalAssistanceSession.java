package com.adalat.entity;

import com.adalat.enums.LegalCategory;
import com.adalat.enums.LegalSessionStatus;
import com.adalat.enums.PracticeArea;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "legal_assistance_sessions")
public class LegalAssistanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private LegalCategory selectedCategory;

    @Enumerated(EnumType.STRING)
    private LegalCategory aiDetectedCategory;

    @Enumerated(EnumType.STRING)
    private PracticeArea practiceArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LegalSessionStatus status = LegalSessionStatus.STARTED;

    @Builder.Default
    private Integer currentQuestionNumber = 0;

    @Builder.Default
    private Integer totalQuestions = 8;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String initialProblemDescription;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LegalChatMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LegalQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LegalAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LegalAssistanceDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "legalSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LawyerSuggestion> lawyerSuggestions = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
