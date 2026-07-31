package com.whatsupmarketplacebackend.service.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsupmarketplacebackend.dto.response.TemplateResponseDTO;
import com.whatsupmarketplacebackend.entity.WhatsAppTemplate;
import com.whatsupmarketplacebackend.enums.TemplateCategory;
import com.whatsupmarketplacebackend.enums.TemplateHeaderType;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.repository.WhatsAppTemplateRepository;
import com.whatsupmarketplacebackend.service.MetaWhatsAppService;
import com.whatsupmarketplacebackend.service.WhatsAppTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DB-backed WhatsApp template service with Meta API sync.
 * Replaces the old in-memory hardcoded template list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WhatsAppTemplateServiceImpl implements WhatsAppTemplateService {

    private final WhatsAppTemplateRepository templateRepository;
    private final MetaWhatsAppService metaWhatsAppService;
    private final ObjectMapper objectMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\d+)}}");

    /**
     * Sync all approved templates from Meta WhatsApp Business API.
     * Performs upsert: creates new templates and updates existing ones.
     */
    public int syncTemplatesFromMeta() {
        log.info("Starting template sync from Meta API...");

        String response = metaWhatsAppService.fetchTemplatesFromMeta();
        int synced = 0;

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                log.warn("No template data array found in Meta API response");
                return 0;
            }

            for (JsonNode templateNode : data) {
                try {
                    syncSingleTemplate(templateNode);
                    synced++;
                } catch (Exception e) {
                    log.error("Failed to sync template: {}", templateNode.path("name").asText(), e);
                }
            }

            log.info("Template sync completed. Synced {} templates.", synced);
            return synced;

        } catch (Exception e) {
            log.error("Failed to parse Meta API template response", e);
            throw new RuntimeException("Template sync failed: " + e.getMessage(), e);
        }
    }

    private void syncSingleTemplate(JsonNode templateNode) {
        String metaTemplateId = templateNode.path("id").asText();
        String name = templateNode.path("name").asText();
        String category = templateNode.path("category").asText();
        String language = templateNode.path("language").asText();
        String status = templateNode.path("status").asText();

        // Parse components
        String body = "";
        String footer = null;
        String headerText = null;
        TemplateHeaderType headerType = TemplateHeaderType.NONE;
        String headerVariables = null;
        String buttons = null;

        JsonNode components = templateNode.path("components");
        if (components.isArray()) {
            for (JsonNode comp : components) {
                String type = comp.path("type").asText().toUpperCase();
                switch (type) {
                    case "HEADER" -> {
                        String format = comp.path("format").asText("TEXT").toUpperCase();
                        headerType = parseHeaderType(format);
                        headerText = comp.path("text").asText(null);
                        if (comp.has("example")) {
                            headerVariables = comp.path("example").toString();
                        }
                    }
                    case "BODY" -> body = comp.path("text").asText("");
                    case "FOOTER" -> footer = comp.path("text").asText(null);
                    case "BUTTONS" -> buttons = comp.path("buttons").toString();
                }
            }
        }

        int variableCount = countVariables(body);

        // Upsert
        WhatsAppTemplate template = templateRepository.findByMetaTemplateId(metaTemplateId)
                .orElse(new WhatsAppTemplate());

        template.setMetaTemplateId(metaTemplateId);
        template.setTemplateName(name);
        template.setCategory(parseCategory(category));
        template.setLanguage(language);
        template.setStatus(status);
        template.setHeaderType(headerType);
        template.setHeaderText(headerText);
        template.setHeaderVariables(headerVariables);
        template.setBody(body);
        template.setFooter(footer);
        template.setButtons(buttons);
        template.setVariableCount(variableCount);

        templateRepository.save(template);
        log.debug("Synced template: {} ({})", name, metaTemplateId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponseDTO> getAllTemplates() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TemplateResponseDTO getTemplateById(Long id) {
        WhatsAppTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with ID: " + id));
        return toDTO(template);
    }

    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new ResourceNotFoundException("Template not found with ID: " + id);
        }
        templateRepository.deleteById(id);
        log.info("Deleted template with ID: {}", id);
    }

    private TemplateResponseDTO toDTO(WhatsAppTemplate t) {
        return TemplateResponseDTO.builder()
                .id(t.getId())
                .templateName(t.getTemplateName())
                .category(t.getCategory() != null ? t.getCategory().name() : null)
                .language(t.getLanguage())
                .status(t.getStatus())
                .headerType(t.getHeaderType() != null ? t.getHeaderType().name() : null)
                .headerText(t.getHeaderText())
                .headerVariables(t.getHeaderVariables())
                .body(t.getBody())
                .footer(t.getFooter())
                .buttons(t.getButtons())
                .variableCount(t.getVariableCount())
                .metaTemplateId(t.getMetaTemplateId())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private int countVariables(String text) {
        if (text == null || text.isBlank()) return 0;
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        int max = 0;
        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1));
            max = Math.max(max, idx);
        }
        return max;
    }

    private TemplateHeaderType parseHeaderType(String format) {
        try {
            return TemplateHeaderType.valueOf(format.toUpperCase());
        } catch (Exception e) {
            return TemplateHeaderType.NONE;
        }
    }

    private TemplateCategory parseCategory(String category) {
        try {
            return TemplateCategory.valueOf(category.toUpperCase());
        } catch (Exception e) {
            return TemplateCategory.MARKETING;
        }
    }
}
