package com.whatsupmarketplacebackend.worker;

import com.whatsupmarketplacebackend.entity.*;
import com.whatsupmarketplacebackend.enums.CampaignStatus;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import com.whatsupmarketplacebackend.enums.TemplateHeaderType;
import com.whatsupmarketplacebackend.repository.CampaignRecipientRepository;
import com.whatsupmarketplacebackend.repository.CampaignRepository;
import com.whatsupmarketplacebackend.repository.CampaignVariableMappingRepository;
import com.whatsupmarketplacebackend.repository.MessageLogRepository;
import com.whatsupmarketplacebackend.service.CampaignLogService;
import com.whatsupmarketplacebackend.service.MessagePreviewService;
import com.whatsupmarketplacebackend.service.MetaWhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Async campaign queue consumer — polls DB for PENDING recipients,
 * sends messages one-by-one with configurable delay.
 *
 * Key design decisions:
 * - One message at a time per campaign (sequential processing)
 * - Checks campaign status before each send (supports pause/cancel)
 * - Crash-safe: PENDING recipients remain in DB after restart
 * - Configurable delay between messages
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignQueueConsumer {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final CampaignVariableMappingRepository variableMappingRepository;
    private final MessageLogRepository messageLogRepository;
    private final MetaWhatsAppService metaWhatsAppService;
    private final MessagePreviewService previewService;
    private final MessageRetryHandler retryHandler;
    private final CampaignLogService campaignLogService;

    /**
     * Process a campaign asynchronously — picks PENDING recipients one by one from DB.
     * This method returns immediately; processing happens in a background thread.
     */
    @Async("campaignTaskExecutor")
    public void processCampaign(Long campaignId) {
        log.info("Campaign worker started for campaign {}", campaignId);

        try {
            Campaign campaign = campaignRepository.findByIdWithTemplateAndClient(campaignId).orElse(null);
            if (campaign == null) {
                log.error("Campaign {} not found. Aborting worker.", campaignId);
                return;
            }

            // Update status to PROCESSING
            campaign.setCampaignStatus(CampaignStatus.PROCESSING);
            campaignRepository.save(campaign);
            campaignLogService.logAction(campaign, "PROCESSING", "Worker started processing messages.");

            // Load variable mappings once (they don't change during processing)
            List<CampaignVariableMapping> mappings =
                    variableMappingRepository.findByCampaignIdOrderByVariableIndexAsc(campaignId);

            int processedCount = 0;

            while (true) {
                // Refresh campaign status from DB (supports pause/cancel)
                campaign = campaignRepository.findByIdWithTemplateAndClient(campaignId).orElse(null);
                if (campaign == null) {
                    log.error("Campaign {} disappeared from DB. Aborting.", campaignId);
                    break;
                }

                // Check for pause/cancel
                if (campaign.getCampaignStatus() == CampaignStatus.PAUSED) {
                    log.info("Campaign {} is PAUSED. Worker stopping.", campaignId);
                    campaignLogService.logAction(campaign, "WORKER_PAUSED", "Worker stopped due to pause.");
                    break;
                }
                if (campaign.getCampaignStatus() == CampaignStatus.CANCELLED) {
                    log.info("Campaign {} is CANCELLED. Worker stopping.", campaignId);
                    break;
                }

                // Poll next PENDING recipient from DB (FIFO by ID)
                List<CampaignRecipient> nextBatch = recipientRepository.findNextPending(
                        campaignId, MessageStatus.PENDING, PageRequest.of(0, 1));

                if (nextBatch.isEmpty()) {
                    // Also check for RETRY recipients
                    List<CampaignRecipient> retryBatch = recipientRepository.findRetryableRecipients(
                            campaignId, 3);
                    if (retryBatch.isEmpty()) {
                        log.info("Campaign {} — no more PENDING or RETRY recipients. Completing.", campaignId);
                        break;
                    }
                    // Process retry recipient
                    processRecipient(campaign, retryBatch.get(0), mappings);
                    processedCount++;
                } else {
                    // Process next pending recipient
                    processRecipient(campaign, nextBatch.get(0), mappings);
                    processedCount++;
                }

                // Configurable delay between messages
                int delay = campaign.getDelayBetweenMessages() != null ? campaign.getDelayBetweenMessages() : 10;
                if (delay > 0) {
                    Thread.sleep(delay * 1000L);
                }
            }

            // Finalize campaign
            campaign = campaignRepository.findByIdWithTemplateAndClient(campaignId).orElse(null);
            if (campaign != null && campaign.getCampaignStatus() == CampaignStatus.PROCESSING) {
                campaign.setCampaignStatus(CampaignStatus.COMPLETED);
                campaign.setCompletedAt(LocalDateTime.now());
                campaignRepository.save(campaign);
                campaignLogService.logAction(campaign, "COMPLETED",
                        "Campaign completed. Processed " + processedCount + " messages.");
            }

            // Update aggregate stats
            updateCampaignStats(campaignId);

            log.info("Campaign worker finished for campaign {}. Processed {} messages.", campaignId, processedCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Campaign {} worker interrupted", campaignId);
        } catch (Exception e) {
            log.error("Campaign {} worker encountered error", campaignId, e);
            try {
                Campaign campaign = campaignRepository.findByIdWithTemplateAndClient(campaignId).orElse(null);
                if (campaign != null) {
                    campaign.setCampaignStatus(CampaignStatus.FAILED);
                    campaignRepository.save(campaign);
                    campaignLogService.logAction(campaign, "FAILED", "Worker error: " + e.getMessage());
                }
            } catch (Exception ex) {
                log.error("Failed to update campaign status to FAILED", ex);
            }
        }
    }

    /**
     * Process a single recipient: resolve variables → send via Meta API → update status.
     */
    private void processRecipient(Campaign campaign,
                                   CampaignRecipient recipient,
                                   List<CampaignVariableMapping> mappings) {
        WhatsAppTemplate template = campaign.getTemplate();
        CustomerData customer = recipient.getCustomer();

        // Mark as SENDING
        recipient.setMessageStatus(MessageStatus.SENDING);
        recipientRepository.save(recipient);

        try {
            // Resolve body variables
            List<String> resolvedVariables = new ArrayList<>();
            for (CampaignVariableMapping mapping : mappings) {
                String value = previewService.resolveCustomerField(customer, mapping.getFieldName());
                if (mapping.getVariableType() == com.whatsupmarketplacebackend.enums.VariableType.STATIC) {
                    value = mapping.getStaticValue();
                }
                resolvedVariables.add(value != null ? value : "");
            }

            // Determine header
            TemplateHeaderType headerType = template.getHeaderType();
            String headerValue = campaign.getHeaderImageUrl() != null
                    ? campaign.getHeaderImageUrl()
                    : template.getHeaderText();

            // Send via Meta API
            Map<String, Object> result = metaWhatsAppService.sendTemplateMessage(
                    recipient.getRecipientPhone(),
                    template.getTemplateName(),
                    template.getLanguage(),
                    headerType,
                    headerValue,
                    resolvedVariables
            );

            if (Boolean.TRUE.equals(result.get("success"))) {
                // SUCCESS
                String messageId = (String) result.get("message_id");
                recipient.setMessageStatus(MessageStatus.SENT);
                recipient.setWhatsappMessageId(messageId);
                recipient.setSentAt(LocalDateTime.now());
                recipientRepository.save(recipient);

                // Update campaign aggregate
                updateCampaignSentCount(campaign);

                // Log message
                saveMessageLog(campaign, customer, messageId, MessageStatus.SENT,
                        null, null, 0,
                        String.valueOf(result.getOrDefault("payload", "")),
                        String.valueOf(result.getOrDefault("response", "")));

                log.debug("Message sent to {} (Campaign {}, Message ID: {})",
                        recipient.getRecipientPhone(), campaign.getId(), messageId);

            } else {
                // FAILURE
                handleSendFailure(campaign, recipient, result);
            }

        } catch (Exception e) {
            log.error("Error processing recipient {} in campaign {}", recipient.getId(), campaign.getId(), e);
            recipient.setMessageStatus(MessageStatus.FAILED);
            recipient.setErrorMessage(e.getMessage());
            recipientRepository.save(recipient);
            updateCampaignFailedCount(campaign);
        }
    }

    private void handleSendFailure(Campaign campaign, CampaignRecipient recipient, Map<String, Object> result) {
        boolean retryable = Boolean.TRUE.equals(result.get("retryable"));
        String errorMsg = String.valueOf(result.getOrDefault("error", "Unknown error"));
        int statusCode = result.containsKey("status_code")
                ? (int) result.get("status_code") : 0;

        if (retryable && retryHandler.canRetry(recipient)) {
            // Schedule retry
            retryHandler.scheduleRetry(recipient, errorMsg);
            updateCampaignRetryCount(campaign);
        } else {
            // Permanent failure
            recipient.setMessageStatus(MessageStatus.FAILED);
            recipient.setErrorMessage(errorMsg);
            recipient.setErrorCode(String.valueOf(statusCode));
            recipientRepository.save(recipient);
            updateCampaignFailedCount(campaign);

            saveMessageLog(campaign, recipient.getCustomer(), null, MessageStatus.FAILED,
                    String.valueOf(statusCode), errorMsg, recipient.getRetryCount(), null, null);
        }
    }

    // ============ Aggregate stat updaters (thread-safe via DB) ============

    @Transactional
    public void updateCampaignStats(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        long sent = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.SENT);
        long delivered = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.DELIVERED);
        long read = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.READ);
        long failed = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.FAILED);
        long retrying = recipientRepository.countByCampaignIdAndMessageStatus(campaignId, MessageStatus.RETRY);

        campaign.setMessagesSent((int) sent);
        campaign.setMessagesDelivered((int) delivered);
        campaign.setMessagesRead((int) read);
        campaign.setMessagesFailed((int) failed);
        campaign.setMessagesRetrying((int) retrying);
        campaignRepository.save(campaign);
    }

    private void updateCampaignSentCount(Campaign campaign) {
        campaign.setMessagesSent((campaign.getMessagesSent() != null ? campaign.getMessagesSent() : 0) + 1);
        campaignRepository.save(campaign);
    }

    private void updateCampaignFailedCount(Campaign campaign) {
        campaign.setMessagesFailed((campaign.getMessagesFailed() != null ? campaign.getMessagesFailed() : 0) + 1);
        campaignRepository.save(campaign);
    }

    private void updateCampaignRetryCount(Campaign campaign) {
        campaign.setMessagesRetrying((campaign.getMessagesRetrying() != null ? campaign.getMessagesRetrying() : 0) + 1);
        campaignRepository.save(campaign);
    }

    private void saveMessageLog(Campaign campaign, CustomerData customer,
                                 String whatsappMessageId, MessageStatus status,
                                 String errorCode, String errorMessage,
                                 int retryCount, String requestPayload, String responsePayload) {
        try {
            MessageLog log = MessageLog.builder()
                    .client(campaign.getClient())
                    .customer(customer)
                    .campaign(campaign)
                    .whatsappMessageId(whatsappMessageId)
                    .recipientNumber(customer.getWhatsappNumber())
                    .templateName(campaign.getTemplate().getTemplateName())
                    .messageStatus(status)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .retryCount(retryCount)
                    .requestPayload(requestPayload)
                    .responsePayload(responsePayload)
                    .build();
            messageLogRepository.save(log);
        } catch (Exception e) {
            CampaignQueueConsumer.log.warn("Failed to save message log for campaign {}", campaign.getId(), e);
        }
    }
}
