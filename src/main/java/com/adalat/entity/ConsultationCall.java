package com.adalat.entity;

import com.adalat.enums.CallStatus;
import com.adalat.enums.CallType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consultation_calls")
public class ConsultationCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long consultationRequestId;

    @Column(nullable = false)
    private Long callerId;

    @Column(nullable = false)
    private String callerRole;

    private String callerName;

    @Column(nullable = false)
    private Long receiverId;

    @Column(nullable = false)
    private String receiverRole;

    private String receiverName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime answeredAt;

    private LocalDateTime endedAt;

    private Integer durationSeconds;

    private String endReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
