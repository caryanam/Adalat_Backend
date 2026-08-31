package com.adalat.service.serviceImpl;

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

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of("pdf", "jpg", "jpeg", "png");

    @Override
    public String storeFile(Long lawyerId, MultipartFile file) throws IOException {
        // Validate file not empty
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty.");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum allowed limit of 10MB.");
        }

        // Validate extension
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("File name is invalid.");
        }

        String extension = getExtension(originalFileName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed types: PDF, JPG, JPEG, PNG.");
        }

        // Build lawyer-specific directory: uploads/lawyers/{lawyerId}/
        Path lawyerDir = Paths.get(lawyerUploadDir, String.valueOf(lawyerId));
        Files.createDirectories(lawyerDir);

        // Generate unique stored filename to avoid collisions
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetPath = lawyerDir.resolve(storedFileName);

        // Write file to disk
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("File stored: {}", targetPath.toAbsolutePath());

        // Build and return HTTP-accessible URL
        // e.g. http://localhost:8082/uploads/lawyers/1/abc123.pdf
        String fileUrl = baseUrl + "/" + lawyerUploadDir + "/" + lawyerId + "/" + storedFileName;
        return fileUrl;
    }

    @Override
    public String getStoredFileName(String fileUrl) {
        // Extract the last segment of the URL
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

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}
