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

    // â”€â”€â”€ Step 1 â€” Account â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String mobileNumber;

    @Column(nullable = false)
    private String password;

    // â”€â”€â”€ Step 2 â€” Professional â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€â”€ Step 3 â€” Documents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LawyerDocument> documents = new ArrayList<>();

    // â”€â”€â”€ Step 4 â€” Pricing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Enumerated(EnumType.STRING)
    private ConsultationRate consultationRate;

    @Builder.Default
    private Integer consultationFee = 99;

    @Builder.Default
    private Integer consultationDuration = 5;

    // â”€â”€â”€ Step 5 â€” UPI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private String upiId;

    // â”€â”€â”€ Status Fields â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€â”€ Profile Enhancements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€â”€ Timestamps â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
