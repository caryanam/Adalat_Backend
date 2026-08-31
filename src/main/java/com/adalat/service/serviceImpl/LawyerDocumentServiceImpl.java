package com.adalat.service.serviceImpl;

import com.adalat.dto.LawyerDocumentResponseDTO;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.enums.DocumentType;
import com.adalat.enums.DocumentVerificationStatus;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.LawyerDocumentRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.service.FileStorageService;
import com.adalat.service.LawyerDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawyerDocumentServiceImpl implements LawyerDocumentService {

    private final LawyerRepository lawyerRepository;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public LawyerDocumentResponseDTO uploadDocument(Long lawyerId, DocumentType documentType, MultipartFile file) {

        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        // Store the physical file and get HTTP URL
        String fileUrl;
        try {
            fileUrl = fileStorageService.storeFile(lawyerId, file);
        } catch (IOException e) {
            log.error("Failed to store file for lawyerId={}", lawyerId, e);
            throw new RuntimeException("Failed to store document. Please try again.");
        }

        String storedFileName = fileStorageService.getStoredFileName(fileUrl);

        // If a document of this type already exists for this lawyer → replace it
        Optional<LawyerDocument> existing = lawyerDocumentRepository.findByLawyerAndDocumentType(lawyer, documentType);
        if (existing.isPresent()) {
            LawyerDocument oldDoc = existing.get();
            // Delete old physical file
            fileStorageService.deleteFile(lawyerId, oldDoc.getStoredFileName());
            // Update existing record
            oldDoc.setOriginalFileName(file.getOriginalFilename());
            oldDoc.setStoredFileName(storedFileName);
            oldDoc.setFileUrl(fileUrl);
            oldDoc.setFileType(file.getContentType());
            oldDoc.setFileSize(file.getSize());
            oldDoc.setVerificationStatus(DocumentVerificationStatus.PENDING);
            LawyerDocument saved = lawyerDocumentRepository.save(oldDoc);
            log.info("Document replaced: lawyerId={}, type={}", lawyerId, documentType);
            return toDTO(saved);
        }

        // Otherwise create a new document record
        LawyerDocument document = LawyerDocument.builder()
                .lawyer(lawyer)
                .documentType(documentType)
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .fileUrl(fileUrl)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .verificationStatus(DocumentVerificationStatus.PENDING)
                .build();

        LawyerDocument saved = lawyerDocumentRepository.save(document);
        log.info("Document uploaded: lawyerId={}, type={}, url={}", lawyerId, documentType, fileUrl);
        return toDTO(saved);
    }

    private LawyerDocumentResponseDTO toDTO(LawyerDocument doc) {
        return LawyerDocumentResponseDTO.builder()
                .id(doc.getId())
                .documentType(doc.getDocumentType())
                .originalFileName(doc.getOriginalFileName())
                .fileUrl(doc.getFileUrl())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .verificationStatus(doc.getVerificationStatus())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
