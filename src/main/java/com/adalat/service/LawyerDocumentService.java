package com.adalat.service;

import com.adalat.dto.LawyerDocumentResponseDTO;
import com.adalat.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

public interface LawyerDocumentService {

    LawyerDocumentResponseDTO uploadDocument(Long lawyerId, DocumentType documentType, MultipartFile file);
}
