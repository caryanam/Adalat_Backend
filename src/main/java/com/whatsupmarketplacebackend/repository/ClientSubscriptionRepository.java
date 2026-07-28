package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.ClientSubscription;
import com.whatsupmarketplacebackend.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientSubscriptionRepository extends JpaRepository<ClientSubscription, Long> {
    
    Optional<ClientSubscription> findByClientIdAndSubscriptionStatus(Long clientId, SubscriptionStatus status);
    
    List<ClientSubscription> findByClientIdOrderByCreatedAtDesc(Long clientId);
    List<ClientSubscription> findBySubscriptionStatusAndExpiryDateBefore(SubscriptionStatus status, LocalDateTime dateTime);

    void deleteByClientId(Long clientId);
}
