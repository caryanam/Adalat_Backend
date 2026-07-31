package com.whatsupmarketplacebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whatsupmarketplacebackend.enums.TemplateHeaderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Generic WhatsApp Cloud API service for sending template messages
 * and fetching templates from Meta Business API.
 *
 * Supports: Text templates, Image/Video/Document headers, Buttons (Quick Reply, URL)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppService {

    private final WebClient metaWhatsAppWebClient;
    private final ObjectMapper objectMapper;

    @Value("${meta.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${meta.whatsapp.business-account-id}")
    private String businessAccountId;

    /**
     * Send a template message to a recipient via Meta WhatsApp Cloud API.
     *
     * @param recipientPhone Phone number with country code (e.g., "919876543210")
     * @param templateName   Approved template name
     * @param language       Template language (e.g., "en")
     * @param headerType     Header type (IMAGE, VIDEO, DOCUMENT, TEXT, NONE)
     * @param headerValue    Header value (URL for media, text for TEXT type)
     * @param bodyVariables  Resolved body variable values in order
     * @return Map with "message_id" on success, or error details on failure
     */
    public Map<String, Object> sendTemplateMessage(String recipientPhone,
                                                    String templateName,
                                                    String language,
                                                    TemplateHeaderType headerType,
                                                    String headerValue,
                                                    List<String> bodyVariables) {

        ObjectNode payload = buildTemplatePayload(recipientPhone, templateName, language,
                                                   headerType, headerValue, bodyVariables);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize WhatsApp payload", e);
            return Map.of("success", false, "error", "Payload serialization failed");
        }

        log.info("Sending WhatsApp template message to {} using template '{}'", recipientPhone, templateName);
        log.debug("Payload: {}", payloadJson);

        try {
            String response = metaWhatsAppWebClient.post()
                    .uri("/" + phoneNumberId + "/messages")
                    .bodyValue(payloadJson)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Meta API error [{}]: {}", clientResponse.statusCode(), body);
                                    return Mono.error(new MetaApiResponseException(
                                            clientResponse.statusCode().value(), body));
                                })
                    )
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            String messageId = responseNode.path("messages").get(0).path("id").asText();

            log.info("Message sent successfully. WhatsApp Message ID: {}", messageId);
            return Map.of("success", true, "message_id", messageId, "response", response);

        } catch (MetaApiResponseException e) {
            log.error("Meta API returned error status {}: {}", e.getStatusCode(), e.getResponseBody());
            return Map.of("success", false,
                          "error", e.getResponseBody(),
                          "status_code", e.getStatusCode(),
                          "retryable", isRetryableError(e.getStatusCode()));
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message", e);
            return Map.of("success", false, "error", e.getMessage(), "retryable", true);
        }
    }

    /**
     * Fetch all message templates from Meta Business API.
     */
    public String fetchTemplatesFromMeta() {
        log.info("Fetching templates from Meta Business API for WABA: {}", businessAccountId);

        try {
            String response = metaWhatsAppWebClient.get()
                    .uri("/" + businessAccountId + "/message_templates?limit=1000")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Meta API error fetching templates: {}", body);
                                    return Mono.error(new RuntimeException("Meta API error: " + body));
                                })
                    )
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully fetched templates from Meta API");
            return response;

        } catch (Exception e) {
            log.error("Failed to fetch templates from Meta API", e);
            throw new RuntimeException("Failed to fetch templates from Meta: " + e.getMessage(), e);
        }
    }

    /**
     * Build the Meta WhatsApp Cloud API template message payload dynamically.
     */
    private ObjectNode buildTemplatePayload(String recipientPhone,
                                             String templateName,
                                             String language,
                                             TemplateHeaderType headerType,
                                             String headerValue,
                                             List<String> bodyVariables) {

        ObjectNode root = objectMapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", recipientPhone);
        root.put("type", "template");

        ObjectNode template = objectMapper.createObjectNode();
        template.put("name", templateName);

        ObjectNode lang = objectMapper.createObjectNode();
        lang.put("code", language);
        template.set("language", lang);

        ArrayNode components = objectMapper.createArrayNode();

        // Header component (if applicable)
        if (headerType != null && headerType != TemplateHeaderType.NONE && headerValue != null) {
            ObjectNode headerComponent = objectMapper.createObjectNode();
            headerComponent.put("type", "header");
            ArrayNode headerParams = objectMapper.createArrayNode();

            switch (headerType) {
                case IMAGE -> {
                    ObjectNode param = objectMapper.createObjectNode();
                    param.put("type", "image");
                    ObjectNode image = objectMapper.createObjectNode();
                    image.put("link", headerValue);
                    param.set("image", image);
                    headerParams.add(param);
                }
                case VIDEO -> {
                    ObjectNode param = objectMapper.createObjectNode();
                    param.put("type", "video");
                    ObjectNode video = objectMapper.createObjectNode();
                    video.put("link", headerValue);
                    param.set("video", video);
                    headerParams.add(param);
                }
                case DOCUMENT -> {
                    ObjectNode param = objectMapper.createObjectNode();
                    param.put("type", "document");
                    ObjectNode document = objectMapper.createObjectNode();
                    document.put("link", headerValue);
                    param.set("document", document);
                    headerParams.add(param);
                }
                case TEXT -> {
                    ObjectNode param = objectMapper.createObjectNode();
                    param.put("type", "text");
                    param.put("text", headerValue);
                    headerParams.add(param);
                }
                default -> { /* NONE — no header */ }
            }

            headerComponent.set("parameters", headerParams);
            components.add(headerComponent);
        }

        // Body component (variable parameters)
        if (bodyVariables != null && !bodyVariables.isEmpty()) {
            ObjectNode bodyComponent = objectMapper.createObjectNode();
            bodyComponent.put("type", "body");
            ArrayNode bodyParams = objectMapper.createArrayNode();

            for (String value : bodyVariables) {
                ObjectNode param = objectMapper.createObjectNode();
                param.put("type", "text");
                param.put("text", value != null ? value : "");
                bodyParams.add(param);
            }

            bodyComponent.set("parameters", bodyParams);
            components.add(bodyComponent);
        }

        template.set("components", components);
        root.set("template", template);

        return root;
    }

    /**
     * Determine if an HTTP status code represents a retryable error.
     */
    private boolean isRetryableError(int statusCode) {
        return statusCode == 408     // Timeout
                || statusCode == 429 // Rate Limit
                || statusCode >= 500; // Server Error
    }

    /**
     * Custom exception for Meta API error responses with status code tracking.
     */
    public static class MetaApiResponseException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        public MetaApiResponseException(int statusCode, String responseBody) {
            super("Meta API error " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
    }
}
