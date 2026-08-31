package com.adalat.entity;

import com.adalat.enums.DocumentType;
import com.adalat.enums.DocumentVerificationStatus;
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
@Table(name = "lawyer_documents")
public class LawyerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private Lawyer lawyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    // Full accessible HTTP URL stored in DB
    @Column(nullable = false, length = 1000)
    private String fileUrl;

    private String fileType;   // e.g., "application/pdf"

    private Long fileSize;     // bytes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentVerificationStatus verificationStatus = DocumentVerificationStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadedAt;
}
