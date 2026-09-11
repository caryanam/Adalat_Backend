package com.adalat.entity;

import com.adalat.enums.MessageType;
import com.adalat.enums.SenderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "legal_intake_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalIntakeMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LegalIntakeSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "client_message_id")
    private String clientMessageId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
