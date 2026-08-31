package com.adalat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerLoginResponseDTO {

    private String token;
    private CustomerInfoDTO customer;
}
