package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.response.CampaignDetailResponseDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignStatsDTO;
import com.whatsupmarketplacebackend.dto.request.CampaignCreateRequestDTO;

import java.util.List;

public interface CampaignService {

    CampaignDetailResponseDTO createCampaign(CampaignCreateRequestDTO request);

    void startCampaign(Long campaignId);

    void pauseCampaign(Long campaignId);

    void resumeCampaign(Long campaignId);

    void cancelCampaign(Long campaignId);

    List<CampaignDetailResponseDTO> getAllCampaigns();

    CampaignDetailResponseDTO getCampaignById(Long campaignId);

    List<CampaignDetailResponseDTO> getCampaignsByClient(Long clientId);

    CampaignStatsDTO getCampaignStats(Long campaignId);
}
