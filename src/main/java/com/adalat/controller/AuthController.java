package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.ForgotPasswordOtpRequestDTO;
import com.adalat.dto.ForgotPasswordResetDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.dto.OtpResponseDTO;
import com.adalat.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth", "/api/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        AuthResponseDTO authResponse = authService.authenticate(loginRequest);
        return ResponseEntity.ok(
                new ApiResponseDTO<>("SUCCESS", "Login successful", authResponse)
        );
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<ApiResponseDTO<OtpResponseDTO>> sendForgotPasswordOtp(@Valid @RequestBody ForgotPasswordOtpRequestDTO request) {
        OtpResponseDTO response = authService.sendForgotPasswordOtp(request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", response.getMessage(), response));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponseDTO<Void>> resetPassword(@Valid @RequestBody ForgotPasswordResetDTO request) {
        authService.resetPasswordWithEmailOtp(request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Password reset successfully. A confirmation has been sent to your email.", null));
    }
}
