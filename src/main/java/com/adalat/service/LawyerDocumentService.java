package com.adalat.service;

import com.adalat.dto.LawyerDocumentResponseDTO;
import com.adalat.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LawyerDocumentService {

    LawyerDocumentResponseDTO uploadDocument(Long lawyerId, DocumentType documentType, MultipartFile file);

    List<LawyerDocumentResponseDTO> getLawyerDocuments(Long lawyerId);

    void deleteDocument(Long lawyerId, DocumentType documentType);
}
