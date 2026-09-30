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

import java.util.Map;

@RestController
@RequestMapping({"/api/auth/email", "/api/auth"})
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailOtpService emailOtpService;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponseDTO<OtpResponseDTO>> sendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        OtpResponseDTO response = emailOtpService.sendRegistrationOtp(request.getEmail(), request.getRole(), "User");
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", response.getMessage(), response));
    }

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

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> checkEmailGet(@RequestParam String email) {
        Map<String, Object> result = emailOtpService.checkEmailAvailability(email);
        boolean exists = Boolean.TRUE.equals(result.get("exists"));
        String status = exists ? "FAIL" : "SUCCESS";
        String message = (String) result.get("message");
        return ResponseEntity.ok(new ApiResponseDTO<>(status, message, result));
    }

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> checkEmailPost(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : "";
        Map<String, Object> result = emailOtpService.checkEmailAvailability(email);
        boolean exists = Boolean.TRUE.equals(result.get("exists"));
        String status = exists ? "FAIL" : "SUCCESS";
        String message = (String) result.get("message");
        return ResponseEntity.ok(new ApiResponseDTO<>(status, message, result));
    }

    @GetMapping("/check-mobile")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> checkMobileGet(@RequestParam String mobile) {
        Map<String, Object> result = emailOtpService.checkMobileAvailability(mobile);
        boolean exists = Boolean.TRUE.equals(result.get("exists"));
        String status = exists ? "FAIL" : "SUCCESS";
        String message = (String) result.get("message");
        return ResponseEntity.ok(new ApiResponseDTO<>(status, message, result));
    }

    @PostMapping("/check-mobile")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> checkMobilePost(@RequestBody Map<String, String> body) {
        String mobile = body != null ? body.get("mobile") : "";
        if (mobile == null || mobile.isBlank()) {
            mobile = body != null ? body.get("mobileNumber") : "";
        }
        Map<String, Object> result = emailOtpService.checkMobileAvailability(mobile);
        boolean exists = Boolean.TRUE.equals(result.get("exists"));
        String status = exists ? "FAIL" : "SUCCESS";
        String message = (String) result.get("message");
        return ResponseEntity.ok(new ApiResponseDTO<>(status, message, result));
    }
}
