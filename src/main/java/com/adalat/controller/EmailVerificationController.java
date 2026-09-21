package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.OtpResponseDTO;
import com.adalat.dto.ResendOtpRequestDTO;
import com.adalat.dto.VerifyOtpRequestDTO;
import com.adalat.enums.Role;
import com.adalat.service.EmailOtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailOtpService emailOtpService;

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponseDTO<OtpResponseDTO>> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        OtpResponseDTO response = emailOtpService.verifyOtp(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", response.getMessage(), response));
        } else {
            return ResponseEntity.ok(new ApiResponseDTO<>("FAILED", response.getMessage(), response));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponseDTO<OtpResponseDTO>> resendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        OtpResponseDTO response = emailOtpService.resendOtp(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", response.getMessage(), response));
        } else {
            return ResponseEntity.ok(new ApiResponseDTO<>("FAILED", response.getMessage(), response));
        }
    }
    
    @GetMapping("/otp-status")
    public ResponseEntity<ApiResponseDTO<OtpResponseDTO>> getOtpStatus(@RequestParam String email, @RequestParam Role role) {
        OtpResponseDTO response = emailOtpService.getOtpStatus(email, role);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", response.getMessage(), response));
    }
}
