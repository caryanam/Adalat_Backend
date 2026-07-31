package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.entity.CampaignLog;
import com.whatsupmarketplacebackend.repository.CampaignLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Campaign lifecycle audit logging service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CampaignLogService {

    private final CampaignLogRepository campaignLogRepository;

    public void logAction(Campaign campaign, String action, String message) {
        logAction(campaign, action, message, "SYSTEM");
    }

    public void logAction(Campaign campaign, String action, String message, String createdBy) {
        CampaignLog logEntry = CampaignLog.builder()
                .campaign(campaign)
                .action(action)
                .message(message)
                .createdBy(createdBy)
                .build();
        campaignLogRepository.save(logEntry);
        log.info("Campaign [{}] - {}: {}", campaign.getId(), action, message);
    }

    @Transactional(readOnly = true)
    public List<CampaignLog> getLogsForCampaign(Long campaignId) {
        return campaignLogRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    }
}
