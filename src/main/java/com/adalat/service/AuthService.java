package com.adalat.service;

import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.ForgotPasswordOtpRequestDTO;
import com.adalat.dto.ForgotPasswordResetDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.dto.OtpResponseDTO;

public interface AuthService {
    
    AuthResponseDTO authenticate(LoginRequestDTO loginRequest);

    OtpResponseDTO sendForgotPasswordOtp(ForgotPasswordOtpRequestDTO request);

    void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword);

    void resetPasswordWithEmailOtp(ForgotPasswordResetDTO request);
}
