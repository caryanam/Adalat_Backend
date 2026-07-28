package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.enums.BusinessCategory;
import com.whatsupmarketplacebackend.enums.Role;

import java.time.LocalDateTime;

public record ClientResponseDTO(

        Long id,
        String ownerName,
        String companyName,
        BusinessCategory category,
        String phoneNumber,
        String whatsappNumber,
        String email,
        Role role,
        LocalDateTime createdAt

) {

    public static ClientResponseDTO from(Client client) {

        return new ClientResponseDTO(

                client.getId(),
                client.getOwnerName(),
                client.getCompanyName(),
                client.getCategory(),
                client.getPhoneNumber(),
                client.getWhatsappNumber(),
                client.getEmail(),
                client.getRole(),
                client.getCreatedAt()
        );
    }
}
