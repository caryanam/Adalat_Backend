package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.request.CampaignCreateRequestDTO;
import com.whatsupmarketplacebackend.dto.request.VariableMappingDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignDetailResponseDTO;
import com.whatsupmarketplacebackend.dto.response.CampaignStatsDTO;
import com.whatsupmarketplacebackend.dto.response.MessagePreviewDTO;
import com.whatsupmarketplacebackend.entity.*;
import com.whatsupmarketplacebackend.enums.CampaignStatus;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import com.whatsupmarketplacebackend.exception.CampaignException;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.queue.CampaignQueueProducer;
import com.whatsupmarketplacebackend.repository.*;
import com.whatsupmarketplacebackend.service.CampaignLogService;
import com.whatsupmarketplacebackend.service.CampaignService;
import com.whatsupmarketplacebackend.service.MessagePreviewService;
import com.whatsupmarketplacebackend.worker.CampaignQueueConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete campaign lifecycle service — create, start, pause, resume, cancel.
 * Campaign processing is fully async and DB-queue backed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final ClientRepository clientRepository;
    private final WhatsAppTemplateRepository templateRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final CampaignVariableMappingRepository variableMappingRepository;
    private final CustomerDataRepository customerDataRepository;
    private final CampaignLogService campaignLogService;
    private final CampaignQueueProducer queueProducer;
    private final CampaignQueueConsumer queueConsumer;
    private final MessagePreviewService previewService;

    @Override
    public CampaignDetailResponseDTO createCampaign(CampaignCreateRequestDTO request) {
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with ID: " + request.getClientId()));

        WhatsAppTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with ID: " + request.getTemplateId()));

        // Build campaign entity
        Campaign campaign = Campaign.builder()
                .client(client)
                .campaignName(request.getCampaignName())
                .template(template)
                .headerImageUrl(request.getHeaderImageUrl())
                .messageLimit(request.getMessageLimit())
                .delayBetweenMessages(request.getDelayBetweenMessages() != null ? request.getDelayBetweenMessages() : 10)
                .campaignStatus(CampaignStatus.CREATED)
                .build();

        Campaign saved = campaignRepository.save(campaign);

        // Save variable mappings
        if (request.getVariableMappings() != null && !request.getVariableMappings().isEmpty()) {
            for (VariableMappingDTO dto : request.getVariableMappings()) {
                CampaignVariableMapping mapping = CampaignVariableMapping.builder()
                        .campaign(saved)
                        .variableIndex(dto.getVariableIndex())
                        .variableType(dto.getVariableType())
                        .fieldName(dto.getFieldName())
                        .staticValue(dto.getStaticValue())
                        .build();
                variableMappingRepository.save(mapping);
            }
        }

        campaignLogService.logAction(saved, "CREATED",
                "Campaign '" + saved.getCampaignName() + "' created for client: " + client.getCompanyName());

        log.info("Campaign created: {} (ID: {})", saved.getCampaignName(), saved.getId());
        return toDetailDTO(saved);
    }

    @Override
    public void startCampaign(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);

        if (campaign.getCampaignStatus() != CampaignStatus.CREATED) {
            throw new CampaignException("Campaign can only be started from CREATED status. Current: "
                    + campaign.getCampaignStatus());
        }

        // Enqueue recipients (this is async-safe — it creates DB records)
        int enqueued = queueProducer.enqueueRecipients(campaign);

        campaign.setTotalRecipients(enqueued);
        campaign.setCampaignStatus(CampaignStatus.QUEUED);
        campaign.setStartedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        campaignLogService.logAction(campaign, "STARTED",
                "Campaign started with " + enqueued + " recipients queued.");

        // Launch async worker
        queueConsumer.processCampaign(campaignId);
    }

    @Override
    public void pauseCampaign(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);

        if (campaign.getCampaignStatus() != CampaignStatus.PROCESSING
                && campaign.getCampaignStatus() != CampaignStatus.QUEUED) {
            throw new CampaignException("Campaign can only be paused when QUEUED or PROCESSING. Current: "
                    + campaign.getCampaignStatus());
        }

        campaign.setCampaignStatus(CampaignStatus.PAUSED);
        campaignRepository.save(campaign);

        campaignLogService.logAction(campaign, "PAUSED", "Campaign paused by admin.");
        log.info("Campaign {} paused", campaignId);
    }

    @Override
    public void resumeCampaign(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);

        if (campaign.getCampaignStatus() != CampaignStatus.PAUSED) {
            throw new CampaignException("Campaign can only be resumed from PAUSED status. Current: "
                    + campaign.getCampaignStatus());
        }

        campaign.setCampaignStatus(CampaignStatus.QUEUED);
        campaignRepository.save(campaign);

        campaignLogService.logAction(campaign, "RESUMED", "Campaign resumed by admin.");

        // Re-launch async worker to continue processing PENDING recipients
        queueConsumer.processCampaign(campaignId);
        log.info("Campaign {} resumed", campaignId);
    }

    @Override
    public void cancelCampaign(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);

        if (campaign.getCampaignStatus() == CampaignStatus.COMPLETED
                || campaign.getCampaignStatus() == CampaignStatus.CANCELLED) {
            throw new CampaignException("Campaign is already " + campaign.getCampaignStatus());
        }

        // Cancel all PENDING recipients
        int cancelled = recipientRepository.updateStatusByCampaignAndCurrentStatus(
                campaignId, MessageStatus.PENDING, MessageStatus.FAILED);

        campaign.setCampaignStatus(CampaignStatus.CANCELLED);
        campaign.setCompletedAt(LocalDateTime.now());
        campaign.setMessagesFailed(campaign.getMessagesFailed() + cancelled);
        campaignRepository.save(campaign);

        campaignLogService.logAction(campaign, "CANCELLED",
                "Campaign cancelled. " + cancelled + " pending messages cancelled.");
        log.info("Campaign {} cancelled. {} pending messages cancelled.", campaignId, cancelled);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignDetailResponseDTO> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDetailDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDetailResponseDTO getCampaignById(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);
        return toDetailDTO(campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignDetailResponseDTO> getCampaignsByClient(Long clientId) {
        return campaignRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::toDetailDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignStatsDTO getCampaignStats(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);

        long pending = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.PENDING);
        long sending = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.SENDING);
        long sent = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.SENT);
        long delivered = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.DELIVERED);
        long read = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.READ);
        long failed = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.FAILED);
        long retrying = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.RETRY);
        long total = recipientRepository.countByCampaignId(campaignId);

        double completion = total > 0 ? ((sent + delivered + read + failed) * 100.0 / total) : 0;
        double deliveryRate = (sent + delivered + read) > 0 ? ((delivered + read) * 100.0 / (sent + delivered + read)) : 0;
        double readRate = (delivered + read) > 0 ? (read * 100.0 / (delivered + read)) : 0;

        return CampaignStatsDTO.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getCampaignName())
                .campaignStatus(campaign.getCampaignStatus().name())
                .totalRecipients((int) total)
                .queued((int) pending)
                .processing((int) sending)
                .sent((int) sent)
                .delivered((int) delivered)
                .read((int) read)
                .failed((int) failed)
                .retrying((int) retrying)
                .cancelled(0)
                .completionPercentage(Math.round(completion * 100.0) / 100.0)
                .deliveryRate(Math.round(deliveryRate * 100.0) / 100.0)
                .readRate(Math.round(readRate * 100.0) / 100.0)
                .build();
    }

    /**
     * Generate message preview for a campaign.
     */
    @Transactional(readOnly = true)
    public MessagePreviewDTO getPreview(Long campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);
        WhatsAppTemplate template = campaign.getTemplate();
        List<CampaignVariableMapping> mappings = variableMappingRepository.findByCampaignIdOrderByVariableIndexAsc(campaignId);

        // Get first customer for preview
        List<CustomerData> customers = customerDataRepository.findByClientId(campaign.getClient().getId());
        CustomerData sampleCustomer = customers.isEmpty() ? null : customers.get(0);

        return previewService.generatePreview(template, mappings, sampleCustomer, campaign.getHeaderImageUrl());
    }

    // ============ Private helpers ============

    private Campaign findCampaignOrThrow(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + campaignId));
    }

    private CampaignDetailResponseDTO toDetailDTO(Campaign c) {
        List<CampaignVariableMapping> mappings = variableMappingRepository
                .findByCampaignIdOrderByVariableIndexAsc(c.getId());

        List<CampaignDetailResponseDTO.VariableMappingResponseDTO> mappingDTOs = mappings.stream()
                .map(m -> CampaignDetailResponseDTO.VariableMappingResponseDTO.builder()
                        .variableIndex(m.getVariableIndex())
                        .variableType(m.getVariableType().name())
                        .fieldName(m.getFieldName())
                        .staticValue(m.getStaticValue())
                        .build())
                .collect(Collectors.toList());

        double completion = c.getTotalRecipients() != null && c.getTotalRecipients() > 0
                ? ((c.getMessagesSent() + c.getMessagesDelivered() + c.getMessagesRead()
                    + c.getMessagesFailed()) * 100.0 / c.getTotalRecipients())
                : 0;

        return CampaignDetailResponseDTO.builder()
                .id(c.getId())
                .campaignName(c.getCampaignName())
                .clientId(c.getClient().getId())
                .clientCompanyName(c.getClient().getCompanyName())
                .templateId(c.getTemplate().getId())
                .templateName(c.getTemplate().getTemplateName())
                .headerImageUrl(c.getHeaderImageUrl())
                .messageLimit(c.getMessageLimit())
                .delayBetweenMessages(c.getDelayBetweenMessages())
                .campaignStatus(c.getCampaignStatus())
                .totalRecipients(c.getTotalRecipients())
                .messagesSent(c.getMessagesSent())
                .messagesDelivered(c.getMessagesDelivered())
                .messagesRead(c.getMessagesRead())
                .messagesFailed(c.getMessagesFailed())
                .messagesRetrying(c.getMessagesRetrying())
                .completionPercentage(Math.round(completion * 100.0) / 100.0)
                .variableMappings(mappingDTOs)
                .startedAt(c.getStartedAt())
                .completedAt(c.getCompletedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
