package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.service.LawyerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lawyers/register")
@RequiredArgsConstructor
public class LawyerRegistrationController {

    private final LawyerService lawyerService;

    // ─── STEP 1 — Account ─────────────────────────────────────────────────────
    @PostMapping("/step1")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> step1(
            @Valid @RequestBody LawyerAccountRequestDTO request) {

        LawyerProfileResponseDTO response = lawyerService.registerStep1(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>("SUCCESS",
                        "Step 1 completed. Please save your Lawyer ID: " + response.getLawyerId(),
                        response));
    }

    // ─── GET Public Advocates Directory ──────────────────────────────────────
    @GetMapping("/directory")
    public ResponseEntity<ApiResponseDTO<java.util.List<LawyerProfileResponseDTO>>> getPublicDirectory() {
        java.util.List<LawyerProfileResponseDTO> response = lawyerService.getPublicDirectoryLawyers();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Public advocate directory retrieved.", response));
    }

    // ─── GET Lawyer Registration Progress ────────────────────────────────────
    @GetMapping("/{lawyerId}")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> getRegistrationProgress(
            @PathVariable Long lawyerId) {

        LawyerProfileResponseDTO response = lawyerService.getLawyerById(lawyerId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Lawyer registration details retrieved.", response));
    }

    // ─── STEP 2 — Professional Details ────────────────────────────────────────
    @PutMapping("/{lawyerId}/step2")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> step2(
            @PathVariable Long lawyerId,
            @Valid @RequestBody LawyerProfessionalRequestDTO request) {

        LawyerProfileResponseDTO response = lawyerService.updateStep2(lawyerId, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Step 2 completed.", response));
    }

    // ─── STEP 4 — Pricing ─────────────────────────────────────────────────────
    @PutMapping("/{lawyerId}/step4")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> step4(
            @PathVariable Long lawyerId,
            @Valid @RequestBody LawyerPricingRequestDTO request) {

        LawyerProfileResponseDTO response = lawyerService.updateStep4(lawyerId, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Step 4 completed.", response));
    }

    // ─── STEP 5 — UPI ─────────────────────────────────────────────────────────
    @PutMapping("/{lawyerId}/step5")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> step5(
            @PathVariable Long lawyerId,
            @Valid @RequestBody LawyerUpiRequestDTO request) {

        LawyerProfileResponseDTO response = lawyerService.updateStep5(lawyerId, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Step 5 completed.", response));
    }

    // ─── STEP 6 — Submit ──────────────────────────────────────────────────────
    @PutMapping("/{lawyerId}/submit")
    public ResponseEntity<ApiResponseDTO<LawyerSubmitResponseDTO>> submit(
            @PathVariable Long lawyerId) {

        LawyerSubmitResponseDTO response = lawyerService.submitApplication(lawyerId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Application submitted successfully. Waiting for admin verification.", response));
    }
}