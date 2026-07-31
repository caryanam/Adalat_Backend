package com.whatsupmarketplacebackend.queue;

import com.whatsupmarketplacebackend.entity.Campaign;
import com.whatsupmarketplacebackend.entity.CampaignRecipient;
import com.whatsupmarketplacebackend.entity.CustomerData;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import com.whatsupmarketplacebackend.repository.CampaignRecipientRepository;
import com.whatsupmarketplacebackend.repository.CustomerDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Campaign queue producer — creates CampaignRecipient records in DB with PENDING status.
 * The campaign_recipients table IS the queue. No external queue needed.
 *
 * Respects messageLimit: only the first N customers are enqueued.
 * Uses batch inserts for performance at scale.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignQueueProducer {

    private final CustomerDataRepository customerDataRepository;
    private final CampaignRecipientRepository recipientRepository;

    @Value("${campaign.batch-insert-size:1000}")
    private int batchInsertSize;

    /**
     * Create CampaignRecipient records for a campaign.
     * Only first `messageLimit` customers are enqueued if limit is set.
     *
     * @return number of recipients enqueued
     */
    @Transactional
    public int enqueueRecipients(Campaign campaign) {
        Long clientId = campaign.getClient().getId();
        Integer messageLimit = campaign.getMessageLimit();

        // Fetch all customers for this client
        List<CustomerData> allCustomers = customerDataRepository.findByClientId(clientId);

        if (allCustomers.isEmpty()) {
            log.warn("No customers found for client {} to enqueue in campaign {}", clientId, campaign.getId());
            return 0;
        }

        // Apply message limit
        int limit = (messageLimit != null && messageLimit > 0)
                ? Math.min(messageLimit, allCustomers.size())
                : allCustomers.size();

        List<CustomerData> customersToQueue = allCustomers.subList(0, limit);

        log.info("Enqueueing {} recipients for campaign {} (total available: {}, limit: {})",
                limit, campaign.getId(), allCustomers.size(),
                messageLimit != null ? messageLimit : "unlimited");

        // Batch insert recipients
        List<CampaignRecipient> batch = new ArrayList<>(batchInsertSize);
        int totalEnqueued = 0;

        for (CustomerData customer : customersToQueue) {
            CampaignRecipient recipient = CampaignRecipient.builder()
                    .campaign(campaign)
                    .customer(customer)
                    .recipientPhone(customer.getWhatsappNumber())
                    .messageStatus(MessageStatus.PENDING)
                    .retryCount(0)
                    .build();
            batch.add(recipient);

            if (batch.size() >= batchInsertSize) {
                recipientRepository.saveAll(batch);
                totalEnqueued += batch.size();
                batch.clear();
                log.debug("Batch inserted {} recipients (total: {})", batchInsertSize, totalEnqueued);
            }
        }

        // Save remaining batch
        if (!batch.isEmpty()) {
            recipientRepository.saveAll(batch);
            totalEnqueued += batch.size();
        }

        log.info("Successfully enqueued {} recipients for campaign {}", totalEnqueued, campaign.getId());
        return totalEnqueued;
    }
}
