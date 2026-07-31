package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Campaign> findByCampaignStatusIn(List<CampaignStatus> statuses);

    List<Campaign> findAllByOrderByCreatedAtDesc();

    void deleteByClientId(Long clientId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Campaign c JOIN FETCH c.template JOIN FETCH c.client WHERE c.id = :id")
    java.util.Optional<Campaign> findByIdWithTemplateAndClient(@org.springframework.data.repository.query.Param("id") Long id);
}
