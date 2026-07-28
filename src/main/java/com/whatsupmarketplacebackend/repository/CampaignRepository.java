package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    
    List<Campaign> findByClientIdOrderByCreatedAtDesc(Long clientId);
    
    List<Campaign> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    void deleteByClientId(Long clientId);
}
