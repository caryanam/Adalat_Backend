package com.adalat.serviceImpl;

import com.adalat.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${app.file.base-url}")
    private String baseUrl;

    @Value("${app.file.lawyer-upload-dir}")
    private String lawyerUploadDir;

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of("pdf", "jpg", "jpeg", "png", "webp", "gif", "doc", "docx", "txt", "rtf", "csv", "xls", "xlsx", "zip");

    @Override
    public String storeFile(Long lawyerId, MultipartFile file) throws IOException {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename()).toLowerCase();

        // Build lawyer-specific directory: uploads/lawyers/{lawyerId}/
        Path lawyerDir = Paths.get(lawyerUploadDir, String.valueOf(lawyerId));
        Files.createDirectories(lawyerDir);

        // Generate unique stored filename to avoid collisions
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetPath = lawyerDir.resolve(storedFileName);

        // Write file to disk
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Lawyer file stored: {}", targetPath.toAbsolutePath());

        // Build and return HTTP-accessible URL
        return baseUrl + "/" + lawyerUploadDir + "/" + lawyerId + "/" + storedFileName;
    }

    @Override
    public String storeCustomerLegalDocument(Long customerId, Long sessionId, MultipartFile file) throws IOException {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename()).toLowerCase();

        // Build customer legal assistance directory: uploads/customers/{customerId}/legal-assistance/{sessionId}/
        Path sessionDir = Paths.get("uploads", "customers", String.valueOf(customerId), "legal-assistance", String.valueOf(sessionId));
        Files.createDirectories(sessionDir);

        String storedFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetPath = sessionDir.resolve(storedFileName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Customer legal document stored: {}", targetPath.toAbsolutePath());

        return baseUrl + "/uploads/customers/" + customerId + "/legal-assistance/" + sessionId + "/" + storedFileName;
    }

    @Override
    public String storeConsultationAttachment(Long consultationId, MultipartFile file) throws IOException {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename()).toLowerCase();

        // Build consultation chat directory: uploads/consultations/{consultationId}/
        Path chatDir = Paths.get("uploads", "consultations", String.valueOf(consultationId));
        Files.createDirectories(chatDir);

        String storedFileName = UUID.randomUUID().toString().replace("-", "") + (extension.isEmpty() ? "" : "." + extension);
        Path targetPath = chatDir.resolve(storedFileName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Consultation attachment stored: {}", targetPath.toAbsolutePath());

        return baseUrl + "/uploads/consultations/" + consultationId + "/" + storedFileName;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum allowed limit of 10MB.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("File name is invalid.");
        }

        String extension = getExtension(originalFileName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed types: PDF, JPG, JPEG, PNG, DOC, DOCX.");
        }
    }

    @Override
    public String getStoredFileName(String fileUrl) {
        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
    }

    @Override
    public void deleteFile(Long lawyerId, String storedFileName) {
        try {
            Path filePath = Paths.get(lawyerUploadDir, String.valueOf(lawyerId), storedFileName);
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not delete file: {} for lawyerId={}", storedFileName, lawyerId);
        }
    }

    @Override
    public void deleteCustomerDirectory(Long customerId) {
        if (customerId == null) return;
        try {
            Path customerDir = Paths.get("uploads", "customers", String.valueOf(customerId));
            if (Files.exists(customerDir)) {
                org.springframework.util.FileSystemUtils.deleteRecursively(customerDir);
                log.info("Recursively deleted customer directory: {}", customerDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not delete customer directory for customerId={}: {}", customerId, e.getMessage());
        }
    }

    @Override
    public void deleteLawyerDirectory(Long lawyerId) {
        if (lawyerId == null) return;
        try {
            Path lawyerDir = Paths.get(lawyerUploadDir, String.valueOf(lawyerId));
            if (Files.exists(lawyerDir)) {
                org.springframework.util.FileSystemUtils.deleteRecursively(lawyerDir);
                log.info("Recursively deleted lawyer directory: {}", lawyerDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not delete lawyer directory for lawyerId={}: {}", lawyerId, e.getMessage());
        }
    }

    @Override
    public void deleteConsultationDirectory(Long consultationId) {
        if (consultationId == null) return;
        try {
            Path consultationDir = Paths.get("uploads", "consultations", String.valueOf(consultationId));
            if (Files.exists(consultationDir)) {
                org.springframework.util.FileSystemUtils.deleteRecursively(consultationDir);
                log.info("Recursively deleted consultation directory: {}", consultationDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not delete consultation directory for consultationId={}: {}", consultationId, e.getMessage());
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}
