package com.whatsupmarketplacebackend.enums;

/**
 * Message delivery lifecycle status.
 * Tracks each recipient's message from creation through delivery.
 */
public enum MessageStatus {
    PENDING,      // Created in DB queue, waiting to be picked up
    SENDING,      // Worker picked up, sending to Meta API
    SENT,         // Meta accepted the message
    DELIVERED,    // Message delivered to recipient device
    READ,         // Recipient read the message
    FAILED,       // Permanently failed (non-retryable or max retries exceeded)
    RETRY         // Temporarily failed, scheduled for retry
}
