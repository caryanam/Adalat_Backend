package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.CampaignRecipient;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    /**
     * DB-Queue: Pick the next PENDING recipient for a campaign (FIFO by ID).
     */
    @Query("SELECT cr FROM CampaignRecipient cr JOIN FETCH cr.customer WHERE cr.campaign.id = :campaignId " +
           "AND cr.messageStatus = :status ORDER BY cr.id ASC")
    List<CampaignRecipient> findNextPending(@Param("campaignId") Long campaignId,
                                             @Param("status") MessageStatus status,
                                             Pageable pageable);

    /**
     * Count recipients by campaign and status (for live stats).
     */
    long countByCampaignIdAndMessageStatus(Long campaignId, MessageStatus messageStatus);

    /**
     * Find recipient by WhatsApp message ID (for webhook status updates).
     */
    Optional<CampaignRecipient> findByWhatsappMessageId(String whatsappMessageId);

    /**
     * Find all recipients for a campaign with specific statuses.
     */
    List<CampaignRecipient> findByCampaignIdAndMessageStatusIn(Long campaignId, List<MessageStatus> statuses);

    /**
     * Batch update status for all PENDING recipients (used for cancel).
     */
    @Modifying
    @Query("UPDATE CampaignRecipient cr SET cr.messageStatus = :newStatus " +
           "WHERE cr.campaign.id = :campaignId AND cr.messageStatus = :currentStatus")
    int updateStatusByCampaignAndCurrentStatus(@Param("campaignId") Long campaignId,
                                                @Param("currentStatus") MessageStatus currentStatus,
                                                @Param("newStatus") MessageStatus newStatus);

    /**
     * Count all recipients for a campaign.
     */
    long countByCampaignId(Long campaignId);

    /**
     * Find recipients with RETRY status that are due for retry.
     */
    @Query("SELECT cr FROM CampaignRecipient cr JOIN FETCH cr.customer WHERE cr.campaign.id = :campaignId " +
           "AND cr.messageStatus = 'RETRY' AND cr.retryCount < :maxRetries ORDER BY cr.id ASC")
    List<CampaignRecipient> findRetryableRecipients(@Param("campaignId") Long campaignId,
                                                     @Param("maxRetries") int maxRetries);
}
