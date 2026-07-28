package com.whatsupmarketplacebackend.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record EnquiryResponseDTO(
    Long id,
    String name,
    String phoneNumber,
    String email,
    String goals,
    LocalDateTime createdAt
) {}
