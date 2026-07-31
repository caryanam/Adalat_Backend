package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.CampaignLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignLogRepository extends JpaRepository<CampaignLog, Long> {

    List<CampaignLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
