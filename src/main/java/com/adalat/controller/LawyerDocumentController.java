package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.LawyerDocumentResponseDTO;
import com.adalat.enums.DocumentType;
import com.adalat.service.LawyerDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/lawyers")
@RequiredArgsConstructor
public class LawyerDocumentController {

    private final LawyerDocumentService lawyerDocumentService;

    @PostMapping("/{lawyerId}/documents")
    public ResponseEntity<ApiResponseDTO<LawyerDocumentResponseDTO>> uploadDocument(
            @PathVariable Long lawyerId,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("file") MultipartFile file) {

        LawyerDocumentResponseDTO response = lawyerDocumentService.uploadDocument(lawyerId, documentType, file);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Document uploaded successfully.", response));
    }

    @GetMapping("/{lawyerId}/documents")
    public ResponseEntity<ApiResponseDTO<List<LawyerDocumentResponseDTO>>> getDocuments(
            @PathVariable Long lawyerId) {

        List<LawyerDocumentResponseDTO> response = lawyerDocumentService.getLawyerDocuments(lawyerId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Lawyer documents retrieved successfully.", response));
    }

    @DeleteMapping("/{lawyerId}/documents/{documentType}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteDocument(
            @PathVariable Long lawyerId,
            @PathVariable DocumentType documentType) {

        lawyerDocumentService.deleteDocument(lawyerId, documentType);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Document deleted successfully.", null));
    }
}
