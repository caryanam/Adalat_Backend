package com.adalat.service;

import com.adalat.dto.OtpResponseDTO;
import com.adalat.dto.ResendOtpRequestDTO;
import com.adalat.dto.VerifyOtpRequestDTO;
import com.adalat.enums.Role;

public interface EmailOtpService {
    void generateAndSendOtp(String email, Role role, String name);
    OtpResponseDTO verifyOtp(VerifyOtpRequestDTO request);
    OtpResponseDTO resendOtp(ResendOtpRequestDTO request);
    OtpResponseDTO getOtpStatus(String email, Role role);
    boolean isEmailVerified(String email, Role role);
}
