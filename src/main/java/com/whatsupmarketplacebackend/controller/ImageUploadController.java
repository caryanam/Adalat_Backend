package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@Tag(name = "Image Upload", description = "Endpoints for uploading campaign and template images (.png, .jpeg, .jpg up to 20MB)")
public class ImageUploadController {

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/png",
            "image/jpeg",
            "image/jpg"
    );
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpeg", "jpg");

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image file (.png, .jpeg, .jpg up to 20MB)", description = "Access Level: Protected [Required Role: ADMIN, CLIENT]")
    public ResponseEntity<ApiResponseDTO<String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO<>("ERROR", "Please select a valid image file to upload.", null));
        }

        // Validate File Size (Max 20MB)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO<>("ERROR", "File size exceeds the 20MB limit. Maximum allowed size is 20MB.", null));
        }

        // Validate File Extension and Format
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean isValidType = (contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase()));
        boolean isValidExt = false;

        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            if (ALLOWED_EXTENSIONS.contains(ext)) {
                isValidExt = true;
            }
        }

        if (!isValidType && !isValidExt) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO<>("ERROR", "Invalid file format. Only PNG, JPEG, and JPG image formats are supported.", null));
        }

        try {
            byte[] bytes = file.getBytes();
            String mimeType = (contentType != null && !contentType.isBlank()) ? contentType : "image/jpeg";
            String base64Image = Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            log.info("Successfully processed uploaded image '{}' (Size: {} MB)", originalFilename, String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));

            return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Image uploaded successfully", dataUrl));

        } catch (Exception e) {
            log.error("Failed to process image upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>("ERROR", "Failed to process uploaded image: " + e.getMessage(), null));
        }
    }
}
