package com.whatsupmarketplacebackend.worker;

import com.whatsupmarketplacebackend.entity.CampaignRecipient;
import com.whatsupmarketplacebackend.enums.MessageStatus;
import com.whatsupmarketplacebackend.repository.CampaignRecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Message retry handler with exponential backoff.
 *
 * Retry policy:
 * - Max 3 attempts
 * - Delay: 10s → 30s → 60s (configurable)
 * - Retryable: Timeout (408), Server Error (500), Rate Limit (429)
 * - Non-retryable: Invalid Number (400), Blocked User, Template Not Found
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageRetryHandler {

    private final CampaignRecipientRepository recipientRepository;

    @Value("${campaign.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${campaign.retry-delay-seconds:10,30,60}")
    private String retryDelaysConfig;

    /**
     * Check if a recipient can be retried.
     */
    public boolean canRetry(CampaignRecipient recipient) {
        return recipient.getRetryCount() < maxRetryAttempts;
    }

    /**
     * Schedule a retry for a failed recipient.
     * Increments retry count and sets status to RETRY.
     */
    public void scheduleRetry(CampaignRecipient recipient, String errorMessage) {
        int currentRetry = recipient.getRetryCount();
        recipient.setRetryCount(currentRetry + 1);
        recipient.setMessageStatus(MessageStatus.RETRY);
        recipient.setErrorMessage(errorMessage);
        recipient.setLastRetryAt(LocalDateTime.now());
        recipientRepository.save(recipient);

        int delay = getRetryDelay(currentRetry);
        log.info("Scheduled retry #{} for recipient {} (campaign {}). Delay: {}s. Error: {}",
                currentRetry + 1, recipient.getId(), recipient.getCampaign().getId(), delay, errorMessage);

        // The delay is applied when the worker picks up RETRY items
        // Worker processes RETRY items after all PENDING items, with appropriate delay
        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // After delay, set back to PENDING so worker picks it up again
        recipient.setMessageStatus(MessageStatus.PENDING);
        recipientRepository.save(recipient);
    }

    /**
     * Determine if an error is retryable based on error context.
     */
    public boolean isRetryableError(String errorCode, String errorMessage) {
        if (errorCode == null) return true; // Assume retryable by default

        int code;
        try {
            code = Integer.parseInt(errorCode);
        } catch (NumberFormatException e) {
            return false;
        }

        // Retryable errors
        if (code == 408 || code == 429 || code >= 500) {
            return true;
        }

        // Non-retryable errors
        if (errorMessage != null) {
            String lower = errorMessage.toLowerCase();
            if (lower.contains("invalid number") ||
                lower.contains("blocked") ||
                lower.contains("template not found") ||
                lower.contains("not a valid whatsapp") ||
                lower.contains("incapable")) {
                return false;
            }
        }

        return code != 400;
    }

    /**
     * Get retry delay for a given attempt number (0-based).
     */
    private int getRetryDelay(int attemptIndex) {
        List<Integer> delays = parseRetryDelays();
        if (attemptIndex < delays.size()) {
            return delays.get(attemptIndex);
        }
        return delays.isEmpty() ? 60 : delays.get(delays.size() - 1);
    }

    private List<Integer> parseRetryDelays() {
        try {
            String[] parts = retryDelaysConfig.split(",");
            return java.util.Arrays.stream(parts)
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .toList();
        } catch (Exception e) {
            return List.of(10, 30, 60);
        }
    }
}
