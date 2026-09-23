package com.adalat.entity;

import com.adalat.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lawyerReg")
public class Lawyer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lawyerId;

    // ─── Step 1 — Account ─────────────────────────────────────────────────────
    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String mobileNumber;

    @Column(nullable = false)
    private String password;

    // ─── Step 2 — Professional ───────
    private String barEnrollmentNumber;

    private Integer yearsOfExperience;

    private String education;

    private String location;

    @ElementCollection(targetClass = PracticeArea.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "lawyer_practice_areas", joinColumns = @JoinColumn(name = "lawyer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "practice_area")
    @Builder.Default
    private Set<PracticeArea> practiceAreas = new HashSet<>();

    @ElementCollection(targetClass = Language.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "lawyer_languages", joinColumns = @JoinColumn(name = "lawyer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    @Builder.Default
    private Set<Language> languages = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String bio;

    // ─── Step 3 — Documents ───────────────────────────────────────────────────
    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LawyerDocument> documents = new ArrayList<>();

    // ─── Step 4 — Pricing ─────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private ConsultationRate consultationRate;

    @Builder.Default
    private Integer consultationFee = 99;

    // ─── Step 5 — UPI ─────────────────────────────────────────────────────────
    private String upiId;

    // ─── Status Fields ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.LAWYER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RegistrationStatus registrationStatus = RegistrationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.INACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    private LocalDateTime emailVerifiedAt;

    // ─── Profile Enhancements ────────────────────────────────────────────────
    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Double rating = 0.0;

    @Builder.Default
    private Integer ratingCount = 0;

    @Builder.Default
    private Integer totalConsultations = 0;

    private String profilePhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // ─── Timestamps ───────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
