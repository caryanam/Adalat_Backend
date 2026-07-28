package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.EnquiryRequestDTO;
import com.whatsupmarketplacebackend.dto.EnquiryResponseDTO;

import java.util.List;

public interface EnquiryService {
    
    EnquiryResponseDTO createEnquiry(EnquiryRequestDTO requestDTO);
    
    List<EnquiryResponseDTO> getAllEnquiries();
    
    EnquiryResponseDTO getEnquiryById(Long id);

}
