package com.whatsupmarketplacebackend.scheduler;

import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.enums.CampaignStatus;
import com.whatsupmarketplacebackend.repository.CampaignRepository;
import com.whatsupmarketplacebackend.service.CampaignLogService;
import com.whatsupmarketplacebackend.worker.CampaignQueueConsumer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Campaign scheduler for:
 * 1. Crash Recovery — re-launch workers for in-progress campaigns on startup
 * 2. Stale Campaign Cleanup — detect stuck campaigns
 * 3. Stats Refresh — periodic aggregate recalculation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignQueueConsumer queueConsumer;
    private final CampaignLogService campaignLogService;

    /**
     * Crash Recovery: On application startup, find campaigns that were
     * PROCESSING or QUEUED when the server went down, and re-launch workers.
     * Recipients are already in DB with PENDING status — no data loss.
     */
    @PostConstruct
    public void recoverInProgressCampaigns() {
        log.info("=== Campaign Crash Recovery: Checking for interrupted campaigns ===");

        List<Campaign> interruptedCampaigns = campaignRepository.findByCampaignStatusIn(
                List.of(CampaignStatus.PROCESSING, CampaignStatus.QUEUED));

        if (interruptedCampaigns.isEmpty()) {
            log.info("No interrupted campaigns found. System is clean.");
            return;
        }

        log.warn("Found {} interrupted campaigns. Re-launching workers...", interruptedCampaigns.size());

        for (Campaign campaign : interruptedCampaigns) {
            try {
                campaignLogService.logAction(campaign, "CRASH_RECOVERY",
                        "Campaign was " + campaign.getCampaignStatus() + " when server restarted. Re-launching worker.");

                campaign.setCampaignStatus(CampaignStatus.QUEUED);
                campaignRepository.save(campaign);

                queueConsumer.processCampaign(campaign.getId());
                log.info("Re-launched worker for campaign {} ({})", campaign.getId(), campaign.getCampaignName());

            } catch (Exception e) {
                log.error("Failed to recover campaign {}", campaign.getId(), e);
            }
        }

        log.info("=== Crash Recovery complete. {} campaigns re-launched. ===", interruptedCampaigns.size());
    }

    /**
     * Stale campaign detector — runs every 30 minutes.
     * Flags campaigns that have been PROCESSING for too long without progress.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void detectStaleCampaigns() {
        List<Campaign> processingCampaigns = campaignRepository.findByCampaignStatusIn(
                List.of(CampaignStatus.PROCESSING));

        for (Campaign campaign : processingCampaigns) {
            if (campaign.getUpdatedAt() != null &&
                    campaign.getUpdatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(30))) {
                log.warn("Campaign {} appears stale (last updated: {}). Consider investigating.",
                        campaign.getId(), campaign.getUpdatedAt());
                campaignLogService.logAction(campaign, "STALE_WARNING",
                        "Campaign has not been updated for 30+ minutes. May be stuck.");
            }
        }
    }

    /**
     * Periodic stats refresh — runs every 5 minutes.
     * Recalculates aggregate stats from campaign_recipients to fix any drift.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void refreshCampaignStats() {
        List<Campaign> activeCampaigns = campaignRepository.findByCampaignStatusIn(
                List.of(CampaignStatus.PROCESSING, CampaignStatus.QUEUED));

        for (Campaign campaign : activeCampaigns) {
            try {
                queueConsumer.updateCampaignStats(campaign.getId());
            } catch (Exception e) {
                log.warn("Failed to refresh stats for campaign {}", campaign.getId(), e);
            }
        }
    }
}
