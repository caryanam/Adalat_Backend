package com.whatsupmarketplacebackend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MarkReadRequestDTO {
    
    @NotEmpty(message = "Notification IDs list cannot be empty")
    private List<Long> notificationIds;
}
