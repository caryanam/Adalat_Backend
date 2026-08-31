package com.adalat.dto;

import com.adalat.enums.DocumentType;
import com.adalat.enums.DocumentVerificationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LawyerDocumentResponseDTO {

    private Long id;
    private DocumentType documentType;
    private String originalFileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private DocumentVerificationStatus verificationStatus;
    private LocalDateTime uploadedAt;
}
