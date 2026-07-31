package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Meta WhatsApp Cloud API Webhook handler.
 *
 * POST /api/webhook/meta — Receives status update events (sent, delivered, read, failed)
 * GET  /api/webhook/meta — Webhook verification (Meta challenge-response)
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @Value("${meta.whatsapp.webhook-verify-token}")
    private String webhookVerifyToken;

    /**
     * Webhook verification endpoint (required by Meta).
     * Meta sends a GET request with hub.mode, hub.verify_token, hub.challenge.
     * If verify_token matches, return hub.challenge.
     */
    @GetMapping("/meta")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        log.info("Webhook verification request received. Mode: {}", mode);

        if ("subscribe".equals(mode) && webhookVerifyToken.equals(token)) {
            log.info("Webhook verified successfully.");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook verification failed. Token mismatch.");
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * Receive webhook events from Meta (message status updates).
     * Must return 200 quickly — processing is done asynchronously.
     */
    @PostMapping("/meta")
    public ResponseEntity<ApiResponseDTO<Object>> receiveWebhook(@RequestBody String payload) {
        log.info("Webhook event received from Meta");

        try {
            webhookService.processWebhook(payload);
        } catch (Exception e) {
            // Always return 200 to Meta — don't let processing errors cause retries
            log.error("Error processing webhook (returning 200 anyway)", e);
        }

        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Webhook processed.", null));
    }
}
