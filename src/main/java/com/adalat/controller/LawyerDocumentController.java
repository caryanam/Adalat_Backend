package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.LawyerDocumentResponseDTO;
import com.adalat.enums.DocumentType;
import com.adalat.service.LawyerDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}
