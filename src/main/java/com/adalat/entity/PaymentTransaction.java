package com.adalat.entity;

import com.adalat.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_request_id")
    private ConsultationRequest consultationRequest;

    @Column(nullable = false, unique = true)
    private String orderId;  // Generated internally or from payment gateway

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal amount = new BigDecimal("99.00");

    @Column(length = 50)
    @Builder.Default
    private String paymentType = "REGISTRATION";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // Filled in after gateway verification
    private String gatewayPaymentId;
    private String gatewaySignature;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
