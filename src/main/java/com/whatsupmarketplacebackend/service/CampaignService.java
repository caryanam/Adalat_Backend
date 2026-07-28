package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.request.CampaignRequestDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignResponseDTO;

import java.util.List;

public interface CampaignService {

    CampaignResponseDTO createCampaign(CampaignRequestDTO request);

    List<CampaignResponseDTO> getAllCampaigns();

    List<CampaignResponseDTO> getCampaignsByClient(Long clientId);
}
