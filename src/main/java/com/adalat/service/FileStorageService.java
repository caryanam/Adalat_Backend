package com.adalat.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    String storeFile(Long lawyerId, MultipartFile file) throws IOException;

    String storeCustomerLegalDocument(Long customerId, Long sessionId, MultipartFile file) throws IOException;

    String getStoredFileName(String fileUrl);

    void deleteFile(Long lawyerId, String storedFileName);
}
