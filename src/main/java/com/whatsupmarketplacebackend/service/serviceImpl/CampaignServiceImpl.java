package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.request.CampaignRequestDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignResponseDTO;
import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.entity.ClientSubscription;
import com.whatsupmarketplacebackend.entity.SubscriptionPlan;
import com.whatsupmarketplacebackend.enums.CampaignStatus;
import com.whatsupmarketplacebackend.enums.SubscriptionStatus;
import com.whatsupmarketplacebackend.exception.CampaignException;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.exception.SubscriptionException;
import com.whatsupmarketplacebackend.repository.CampaignRepository;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.repository.ClientSubscriptionRepository;
import com.whatsupmarketplacebackend.service.CampaignService;
import com.whatsupmarketplacebackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final ClientRepository clientRepository;
    private final ClientSubscriptionRepository subscriptionRepository;
    private final com.whatsupmarketplacebackend.repository.CustomerDataRepository customerDataRepository;
    private final com.whatsupmarketplacebackend.repository.MessageLogRepository messageLogRepository;
    private final NotificationService notificationService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public CampaignResponseDTO createCampaign(CampaignRequestDTO request) {

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        ClientSubscription activeSub = subscriptionRepository.findByClientIdAndSubscriptionStatus(request.getClientId(), SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionException("Client does not have an active subscription"));

        if (activeSub.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new SubscriptionException("Subscription has expired. Cannot create campaign.");
        }

        if (activeSub.getCampaignUsed() >= activeSub.getSubscriptionPlan().getCampaignLimit()) {
            throw new CampaignException("Campaign Limit Exceeded. Maximum allowed: " + activeSub.getSubscriptionPlan().getCampaignLimit());
        }

        // Calculate target contacts and remaining quota
        long targetContacts = customerDataRepository.countByClientId(request.getClientId());
        int remainingQuota = activeSub.getRemainingMessages() != null ? activeSub.getRemainingMessages() : (activeSub.getSubscriptionPlan() != null ? activeSub.getSubscriptionPlan().getMessageLimit() : 0);
        int dispatchCount = 0;
        if (targetContacts > 0 && remainingQuota > 0) {
            dispatchCount = (int) Math.min(targetContacts, (long) remainingQuota);
        } else if (targetContacts > 0) {
            dispatchCount = (int) targetContacts;
        }

        Campaign campaign = new Campaign();
        campaign.setClient(client);
        campaign.setSubscription(activeSub);
        campaign.setCampaignName(request.getCampaignName());
        campaign.setTemplateId(request.getTemplateId());
        campaign.setHeaderImageUrl(request.getHeaderImageUrl());
        if (request.getTemplateValues() != null) {
            campaign.setTemplateValuesJson(request.getTemplateValues().toString());
        }
        campaign.setCampaignStatus(dispatchCount > 0 ? CampaignStatus.RUNNING : CampaignStatus.CREATED);
        campaign.setMessagesSent(dispatchCount);

        Campaign savedCampaign = campaignRepository.save(campaign);

        // Update client usage (campaigns used and remaining messages)
        activeSub.setCampaignUsed((activeSub.getCampaignUsed() != null ? activeSub.getCampaignUsed() : 0) + 1);
        activeSub.setRemainingMessages(Math.max(0, remainingQuota - dispatchCount));
        subscriptionRepository.save(activeSub);

        notificationService.sendNotification(client.getEmail(), "New Campaign Created",
                "A new campaign '" + request.getCampaignName() + "' has been created for your account.", com.whatsupmarketplacebackend.enums.NotificationType.CAMPAIGN);

        return mapToDTO(savedCampaign);
    }

    @Override
    public List<CampaignResponseDTO> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CampaignResponseDTO> getCampaignsByClient(Long clientId) {
        return campaignRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    private CampaignResponseDTO mapToDTO(Campaign campaign) {
        CampaignResponseDTO dto = modelMapper.map(campaign, CampaignResponseDTO.class);
        if (campaign.getSubscription() != null) {
            dto.setRemainingMessages(campaign.getSubscription().getRemainingMessages());
        }
        return dto;
    }
}
