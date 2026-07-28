package com.whatsupmarketplacebackend.service;

public interface PasswordResetService {

    void forgotPassword(String email);

    void verifyOtp(String email, String otp);

    void resetPassword(String email, String newPassword);
}
