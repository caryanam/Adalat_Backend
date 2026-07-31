package com.whatsupmarketplacebackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsupmarketplacebackend.dto.response.MessagePreviewDTO;
import com.whatsupmarketplacebackend.entity.CampaignVariableMapping;
import com.whatsupmarketplacebackend.entity.CustomerData;
import com.whatsupmarketplacebackend.entity.WhatsAppTemplate;
import com.whatsupmarketplacebackend.enums.TemplateHeaderType;
import com.whatsupmarketplacebackend.enums.VariableType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates WhatsApp message preview by resolving template variables
 * against sample customer data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePreviewService {

    private final ObjectMapper objectMapper;

    /**
     * Generate a preview of the final WhatsApp message.
     */
    public MessagePreviewDTO generatePreview(WhatsAppTemplate template,
                                              List<CampaignVariableMapping> mappings,
                                              CustomerData sampleCustomer,
                                              String headerImageUrl) {

        String resolvedBody = resolveVariables(template.getBody(), mappings, sampleCustomer);
        String resolvedHeaderText = resolveVariables(template.getHeaderText(), mappings, sampleCustomer);

        List<MessagePreviewDTO.ButtonPreview> buttonPreviews = parseButtons(template.getButtons());

        return MessagePreviewDTO.builder()
                .templateName(template.getTemplateName())
                .category(template.getCategory() != null ? template.getCategory().name() : null)
                .headerType(template.getHeaderType() != null ? template.getHeaderType().name() : "NONE")
                .headerImageUrl(headerImageUrl != null ? headerImageUrl :
                        (template.getHeaderType() == TemplateHeaderType.IMAGE ? template.getHeaderText() : null))
                .headerText(resolvedHeaderText)
                .body(resolvedBody)
                .footer(template.getFooter())
                .buttons(buttonPreviews)
                .build();
    }

    /**
     * Resolve {{1}}, {{2}}, etc. in template text using variable mappings and customer data.
     */
    public String resolveVariables(String text,
                                    List<CampaignVariableMapping> mappings,
                                    CustomerData customer) {
        if (text == null || text.isBlank() || mappings == null || mappings.isEmpty()) {
            return text;
        }

        String resolved = text;
        for (CampaignVariableMapping mapping : mappings) {
            String placeholder = "{{" + mapping.getVariableIndex() + "}}";
            String value;

            if (mapping.getVariableType() == VariableType.STATIC) {
                value = mapping.getStaticValue() != null ? mapping.getStaticValue() : "";
            } else {
                value = resolveCustomerField(customer, mapping.getFieldName());
            }

            resolved = resolved.replace(placeholder, value != null ? value : "");
        }
        return resolved;
    }

    /**
     * Resolve a customer field value by field name.
     */
    public String resolveCustomerField(CustomerData customer, String fieldName) {
        if (customer == null || fieldName == null) return "";

        return switch (fieldName.toLowerCase().trim()) {
            case "customer_name", "name" -> customer.getCustomerName();
            case "whatsapp_number", "mobile", "phone" -> customer.getWhatsappNumber();
            case "city" -> customer.getCity();
            case "state" -> customer.getState();
            case "business_name", "business" -> customer.getBusinessName();
            default -> {
                // Try dynamic fields (JSON)
                if (customer.getDynamicFields() != null) {
                    try {
                        Map<String, String> dynamicMap = objectMapper.readValue(
                                customer.getDynamicFields(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        yield dynamicMap.getOrDefault(fieldName, "");
                    } catch (Exception e) {
                        log.warn("Failed to parse dynamic fields for customer {}", customer.getId());
                    }
                }
                yield "";
            }
        };
    }

    private List<MessagePreviewDTO.ButtonPreview> parseButtons(String buttonsJson) {
        List<MessagePreviewDTO.ButtonPreview> previews = new ArrayList<>();
        if (buttonsJson == null || buttonsJson.isBlank()) return previews;

        try {
            List<Map<String, Object>> buttons = objectMapper.readValue(
                    buttonsJson, new TypeReference<>() {});
            for (Map<String, Object> btn : buttons) {
                previews.add(MessagePreviewDTO.ButtonPreview.builder()
                        .type(String.valueOf(btn.getOrDefault("type", "")))
                        .text(String.valueOf(btn.getOrDefault("text", "")))
                        .url(String.valueOf(btn.getOrDefault("url", "")))
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse buttons JSON: {}", e.getMessage());
        }
        return previews;
    }
}
