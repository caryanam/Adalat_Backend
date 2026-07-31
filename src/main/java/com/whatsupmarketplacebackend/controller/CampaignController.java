package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.request.CampaignCreateRequestDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignDetailResponseDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignStatsDTO;
import com.whatsupmarketplacebackend.dto.response.MessagePreviewDTO;
import com.whatsupmarketplacebackend.service.CampaignService;
import com.whatsupmarketplacebackend.service.serviceImpl.CampaignServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Campaign management REST API.
 * Full lifecycle: create → start → pause → resume → cancel.
 * Campaign start returns immediately — processing is async.
 */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignServiceImpl campaignServiceImpl; // For preview method

    /**
     * Create a new campaign and automatically start processing it in the background.
     */
    @PostMapping
    public ResponseEntity<ApiResponseDTO<CampaignDetailResponseDTO>> createCampaign(
            @Valid @RequestBody CampaignCreateRequestDTO request) {
        // 1. Create the campaign and queue recipients
        CampaignDetailResponseDTO campaign = campaignService.createCampaign(request);
        
        // 2. Automatically start the async worker
        campaignService.startCampaign(campaign.getId());
        
        // Update the status in the DTO before returning
        campaign.setCampaignStatus(com.whatsupmarketplacebackend.enums.CampaignStatus.PROCESSING);
        
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign created and started successfully. Messages are being processed in the background.", campaign));
    }

    /**
     * Start a campaign — creates recipient records and launches async worker.
     * Returns immediately (non-blocking).
     */
    @PostMapping("/start/{id}")
    public ResponseEntity<ApiResponseDTO<Object>> startCampaign(@PathVariable Long id) {
        campaignService.startCampaign(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign started. Messages are being queued and processed in background.", null));
    }

    /**
     * Pause a running campaign — worker will stop after current message.
     */
    @PostMapping("/pause/{id}")
    public ResponseEntity<ApiResponseDTO<Object>> pauseCampaign(@PathVariable Long id) {
        campaignService.pauseCampaign(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign paused successfully. Pending messages will wait.", null));
    }

    /**
     * Resume a paused campaign — re-launches async worker.
     */
    @PostMapping("/resume/{id}")
    public ResponseEntity<ApiResponseDTO<Object>> resumeCampaign(@PathVariable Long id) {
        campaignService.resumeCampaign(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign resumed. Processing will continue from where it left off.", null));
    }

    /**
     * Cancel a campaign — cancels all pending messages.
     */
    @PostMapping("/cancel/{id}")
    public ResponseEntity<ApiResponseDTO<Object>> cancelCampaign(@PathVariable Long id) {
        campaignService.cancelCampaign(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign cancelled. All pending messages have been cancelled.", null));
    }

    /**
     * Get all campaigns.
     */
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<CampaignDetailResponseDTO>>> getAllCampaigns() {
        List<CampaignDetailResponseDTO> campaigns = campaignService.getAllCampaigns();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaigns fetched successfully.", campaigns));
    }

    /**
     * Get campaign by ID with full details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<CampaignDetailResponseDTO>> getCampaignById(@PathVariable Long id) {
        CampaignDetailResponseDTO campaign = campaignService.getCampaignById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign fetched successfully.", campaign));
    }

    /**
     * Get live campaign statistics (queued, processing, sent, delivered, read, failed, etc.)
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponseDTO<CampaignStatsDTO>> getCampaignStats(@PathVariable Long id) {
        CampaignStatsDTO stats = campaignService.getCampaignStats(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Campaign stats fetched successfully.", stats));
    }

    /**
     * Get WhatsApp message preview with resolved template variables.
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponseDTO<MessagePreviewDTO>> getCampaignPreview(@PathVariable Long id) {
        MessagePreviewDTO preview = campaignServiceImpl.getPreview(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Message preview generated successfully.", preview));
    }
}
