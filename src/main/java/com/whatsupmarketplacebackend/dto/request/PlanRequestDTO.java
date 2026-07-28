package com.whatsupmarketplacebackend.dto.request;

import com.whatsupmarketplacebackend.enums.PlanCode;
import com.whatsupmarketplacebackend.enums.PlanType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanRequestDTO {
    
    @NotBlank(message = "Plan name is required")
    private String planName;
    
    @NotNull(message = "Plan code is required")
    private PlanCode planCode;
    
    @NotNull(message = "Plan type is required")
    private PlanType planType;
    
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private Double price;
    
    @NotNull(message = "Message limit is required")
    @Min(value = 1, message = "Message limit must be at least 1")
    private Integer messageLimit;
    
    @NotNull(message = "Campaign limit is required")
    @Min(value = 1, message = "Campaign limit must be at least 1")
    private Integer campaignLimit;
    
    @NotNull(message = "Validity days is required")
    @Min(value = 1, message = "Validity days must be at least 1")
    private Integer validityDays;
    
    private Boolean isActive = true;
}
