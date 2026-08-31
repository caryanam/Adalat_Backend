package com.adalat.dto;

import com.adalat.enums.LegalDocumentType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalDocumentResponseDTO {

    private Long id;
    private Long sessionId;
    private LegalDocumentType documentType;
    private String originalFileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}
