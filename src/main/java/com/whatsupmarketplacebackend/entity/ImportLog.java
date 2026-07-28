package com.whatsupmarketplacebackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private int totalRowsRead;

    @Column(nullable = false)
    private int totalImported;

    @Column(nullable = false)
    private int skippedEmptyRows;

    @Column(nullable = false)
    private int skippedInvalidRows;

    @Column(nullable = false)
    private int skippedDuplicateRows;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime importedAt;
}
