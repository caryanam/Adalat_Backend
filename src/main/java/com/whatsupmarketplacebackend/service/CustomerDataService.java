package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.ExcelImportResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerDataService {
    ExcelImportResponseDTO importFromExcel(MultipartFile file, Long clientId, com.whatsupmarketplacebackend.enums.BusinessCategory businessCategory);
    java.util.List<com.whatsupmarketplacebackend.dto.CustomerDataResponseDTO> getCustomerDataByClientId(Long clientId);
    com.whatsupmarketplacebackend.dto.ImportLogResponseDTO getLatestImportStatByClientId(Long clientId);
}
