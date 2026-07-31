package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.CampaignVariableMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignVariableMappingRepository extends JpaRepository<CampaignVariableMapping, Long> {

    List<CampaignVariableMapping> findByCampaignIdOrderByVariableIndexAsc(Long campaignId);

    void deleteByCampaignId(Long campaignId);
}
