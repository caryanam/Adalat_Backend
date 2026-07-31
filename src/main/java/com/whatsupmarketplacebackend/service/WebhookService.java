package com.whatsupmarketplacebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.entity.CampaignRecipient;
import com.whatsupmarketplacebackend.entity.WebhookLog;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import com.whatsupmarketplacebackend.enums.WebhookEventType;
import com.whatsupmarketplacebackend.repository.CampaignRecipientRepository;
import com.whatsupmarketplacebackend.repository.CampaignRepository;
import com.whatsupmarketplacebackend.repository.WebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Webhook service for processing Meta WhatsApp status updates.
 *
 * Meta webhook structure:
 * entry[] → changes[] → value → statuses[] → { id, status, timestamp, recipient_id }
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookService {

    private final CampaignRecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process incoming webhook payload from Meta.
     */
    public void processWebhook(String payload) {
        log.info("Processing Meta webhook payload");
        log.debug("Webhook payload: {}", payload);

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode entries = root.path("entry");

            if (!entries.isArray()) {
                log.warn("No 'entry' array in webhook payload");
                return;
            }

            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;

                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");
                    processStatusUpdates(value, payload);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process webhook payload", e);
        }
    }

    private void processStatusUpdates(JsonNode value, String rawPayload) {
        JsonNode statuses = value.path("statuses");
        if (!statuses.isArray()) return;

        for (JsonNode statusNode : statuses) {
            String whatsappMessageId = statusNode.path("id").asText(null);
            String status = statusNode.path("status").asText(null);
            String recipientPhone = statusNode.path("recipient_id").asText(null);
            long timestamp = statusNode.path("timestamp").asLong(0);

            if (whatsappMessageId == null || status == null) {
                log.warn("Skipping webhook status update with missing id or status");
                continue;
            }

            // Parse event type
            WebhookEventType eventType = parseEventType(status);
            MessageStatus messageStatus = mapToMessageStatus(status);

            // Save webhook log
            saveWebhookLog(whatsappMessageId, eventType, recipientPhone, timestamp, rawPayload);

            // Update campaign recipient status
            recipientRepository.findByWhatsappMessageId(whatsappMessageId)
                    .ifPresentOrElse(
                            recipient -> updateRecipientStatus(recipient, messageStatus, timestamp),
                            () -> log.warn("No recipient found for WhatsApp message ID: {}", whatsappMessageId)
                    );
        }
    }

    private void updateRecipientStatus(CampaignRecipient recipient, MessageStatus newStatus, long timestamp) {
        // Only update if new status is a progression (e.g., SENT → DELIVERED → READ)
        if (isStatusProgression(recipient.getMessageStatus(), newStatus)) {
            LocalDateTime eventTime = timestamp > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
                    : LocalDateTime.now();

            recipient.setMessageStatus(newStatus);

            switch (newStatus) {
                case DELIVERED -> recipient.setDeliveredAt(eventTime);
                case READ -> recipient.setReadAt(eventTime);
                default -> { /* SENT is already tracked at send time */ }
            }

            recipientRepository.save(recipient);

            // Update campaign aggregate stats
            Campaign campaign = recipient.getCampaign();
            updateCampaignAggregateForWebhook(campaign, newStatus);

            log.info("Updated recipient {} status to {} (WhatsApp msg: {})",
                    recipient.getId(), newStatus, recipient.getWhatsappMessageId());
        }
    }

    private void updateCampaignAggregateForWebhook(Campaign campaign, MessageStatus newStatus) {
        switch (newStatus) {
            case DELIVERED -> campaign.setMessagesDelivered(
                    (campaign.getMessagesDelivered() != null ? campaign.getMessagesDelivered() : 0) + 1);
            case READ -> campaign.setMessagesRead(
                    (campaign.getMessagesRead() != null ? campaign.getMessagesRead() : 0) + 1);
            case FAILED -> campaign.setMessagesFailed(
                    (campaign.getMessagesFailed() != null ? campaign.getMessagesFailed() : 0) + 1);
            default -> { /* No aggregate update needed for other statuses */ }
        }
        campaignRepository.save(campaign);
    }

    /**
     * Status progression: PENDING → SENDING → SENT → DELIVERED → READ
     * Prevents backward transitions (e.g., READ → DELIVERED).
     */
    private boolean isStatusProgression(MessageStatus current, MessageStatus next) {
        int currentOrd = getStatusOrder(current);
        int nextOrd = getStatusOrder(next);
        return nextOrd > currentOrd;
    }

    private int getStatusOrder(MessageStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case SENDING -> 1;
            case SENT -> 2;
            case DELIVERED -> 3;
            case READ -> 4;
            case FAILED -> 5;
            case RETRY -> 1;
        };
    }

    private MessageStatus mapToMessageStatus(String metaStatus) {
        return switch (metaStatus.toLowerCase()) {
            case "accepted", "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            default -> MessageStatus.SENT;
        };
    }

    private WebhookEventType parseEventType(String status) {
        try {
            return WebhookEventType.valueOf(status.toUpperCase());
        } catch (Exception e) {
            return WebhookEventType.SENT;
        }
    }

    private void saveWebhookLog(String whatsappMessageId, WebhookEventType eventType,
                                 String recipientPhone, long timestamp, String rawPayload) {
        try {
            WebhookLog webhookLog = WebhookLog.builder()
                    .whatsappMessageId(whatsappMessageId)
                    .eventType(eventType)
                    .recipientPhone(recipientPhone)
                    .eventTimestamp(timestamp > 0
                            ? LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
                            : null)
                    .rawPayload(rawPayload)
                    .processedAt(LocalDateTime.now())
                    .build();
            webhookLogRepository.save(webhookLog);
        } catch (Exception e) {
            log.warn("Failed to save webhook log for message {}", whatsappMessageId, e);
        }
    }
}
