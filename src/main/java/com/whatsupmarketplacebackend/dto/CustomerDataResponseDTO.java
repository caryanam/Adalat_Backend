package com.whatsupmarketplacebackend.dto;

import com.whatsupmarketplacebackend.entity.CustomerData;

import java.time.LocalDateTime;

public record CustomerDataResponseDTO(

        Long id,
        String customerName,
        String whatsappNumber,
        Long clientId,
        com.whatsupmarketplacebackend.enums.BusinessCategory businessCategory,
        LocalDateTime createdAt

) {

    public static CustomerDataResponseDTO from(CustomerData customerData) {

        return new CustomerDataResponseDTO(
                customerData.getId(),
                customerData.getCustomerName(),
                customerData.getWhatsappNumber(),
                customerData.getClient() != null ? customerData.getClient().getId() : null,
                customerData.getBusinessCategory(),
                customerData.getCreatedAt()
        );
    }
}
