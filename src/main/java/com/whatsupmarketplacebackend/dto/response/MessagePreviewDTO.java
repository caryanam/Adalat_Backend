package com.whatsupmarketplacebackend.dto.response;

import lombok.*;

import java.util.List;

/**
 * WhatsApp message preview — shows how the final message will look
 * with variables resolved against sample customer data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessagePreviewDTO {

    private String headerType;
    private String headerImageUrl;
    private String headerText;
    private String body;
    private String footer;
    private List<ButtonPreview> buttons;

    // Template metadata
    private String templateName;
    private String category;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ButtonPreview {
        private String type;
        private String text;
        private String url;
    }
}
