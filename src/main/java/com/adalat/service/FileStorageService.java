package com.adalat.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    /**
     * Saves the file to disk under the lawyer's sub-directory and returns
     * the full HTTP-accessible URL that will be stored in the database.
     *
     * @param lawyerId the ID of the lawyer (used as a sub-folder)
     * @param file     the uploaded file
     * @return full HTTP URL, e.g. http://localhost:8082/uploads/lawyers/1/aadhaar-abc123.pdf
     */
    String storeFile(Long lawyerId, MultipartFile file) throws IOException;

    /**
     * Returns just the stored filename from a full URL (used to locate file for deletion).
     */
    String getStoredFileName(String fileUrl);

    /**
     * Deletes the physical file from disk given the stored filename and lawyerId.
     */
    void deleteFile(Long lawyerId, String storedFileName);
}
