package com.adalat.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String otp);
    void sendEmailChangeOtp(String to, String name, String otp);
    void sendPasswordChangeAlert(String to, String name);
}
