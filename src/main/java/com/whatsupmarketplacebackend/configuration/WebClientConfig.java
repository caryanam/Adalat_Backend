package com.whatsupmarketplacebackend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for Meta WhatsApp Cloud API integration.
 * Pre-configures base URL, auth header, and buffer limits.
 */
@Configuration
public class WebClientConfig {

    @Value("${meta.whatsapp.base-url}")
    private String baseUrl;

    @Value("${meta.whatsapp.api-version}")
    private String apiVersion;

    @Value("${meta.whatsapp.access-token}")
    private String accessToken;

    @Bean
    public WebClient metaWhatsAppWebClient() {
        // Increase buffer size for large template sync responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)) // 5MB
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl + "/" + apiVersion)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
    }
}
