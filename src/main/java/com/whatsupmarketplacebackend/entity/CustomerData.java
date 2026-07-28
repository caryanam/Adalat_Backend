package com.whatsupmarketplacebackend.entity;

import com.whatsupmarketplacebackend.enums.BusinessCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_data", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"client_id", "whatsapp_number"})
       },
       indexes = {
           @Index(name = "idx_client_id", columnList = "client_id"),
           @Index(name = "idx_client_business", columnList = "client_id, business_category")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(name = "whatsapp_number", nullable = false)
    private String whatsappNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_category")
    private BusinessCategory businessCategory;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
