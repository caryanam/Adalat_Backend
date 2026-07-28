package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.entity.Admin;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.entity.PasswordResetOtp;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.repository.AdminRepository;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.repository.PasswordResetOtpRepository;
import com.whatsupmarketplacebackend.service.PasswordResetService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final AdminRepository adminRepository;
    private final ClientRepository clientRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void forgotPassword(String email) {

        // Find the user's name from either Admin or Client table
        String userName = findUserName(email);

        // Generate 6-digit OTP
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));

        // Save OTP record
        PasswordResetOtp otpEntity = PasswordResetOtp.builder()
                .email(email)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .verified(false)
                .build();

        otpRepository.save(otpEntity);

        // Send OTP via email asynchronously to prevent blocking the HTTP response
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            sendOtpEmail(email, otp, userName);
        });

        log.info("OTP email sending initiated for: {}", email);
    }

    @Override
    public void verifyOtp(String email, String otp) {

        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No OTP found for email: " + email
                ));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }

        if (!otpEntity.getOtp().equals(otp)) {
            throw new IllegalArgumentException("Invalid OTP.");
        }

        otpEntity.setVerified(true);
        otpRepository.save(otpEntity);

        log.info("OTP verified for email: {}", email);
    }

    @Override
    public void resetPassword(String email, String newPassword) {

        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No OTP found for email: " + email
                ));

        if (!otpEntity.getVerified()) {
            throw new IllegalArgumentException("OTP is not verified. Please verify OTP first.");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }

        // Update password in the correct table
        String encodedPassword = passwordEncoder.encode(newPassword);

        Optional<Admin> admin = adminRepository.findByEmail(email);
        if (admin.isPresent()) {
            admin.get().setPassword(encodedPassword);
            adminRepository.save(admin.get());
            log.info("Password reset successfully for admin: {}", email);
        } else {
            Optional<Client> client = clientRepository.findByEmail(email);
            if (client.isPresent()) {
                client.get().setPassword(encodedPassword);
                clientRepository.save(client.get());
                log.info("Password reset successfully for client: {}", email);
            } else {
                throw new ResourceNotFoundException(
                        "No account found with email: " + email
                );
            }
        }

        // Cleanup OTP records for this email
        otpRepository.deleteByEmail(email);
    }

    private String findUserName(String email) {
        Optional<Admin> admin = adminRepository.findByEmail(email);
        if (admin.isPresent()) {
            return admin.get().getFullName();
        }

        Optional<Client> client = clientRepository.findByEmail(email);
        if (client.isPresent()) {
            return client.get().getOwnerName();
        }

        throw new ResourceNotFoundException("No account found with email: " + email);
    }

    private void sendOtpEmail(String toEmail, String otp, String userName) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("support@whatsupmarketplace.com");
            helper.setTo(toEmail);
            helper.setSubject("Whatsup Market Place - Password Reset Request");
            helper.setText(buildOtpEmailHtml(userName, otp), true);

            mailSender.send(mimeMessage);
        } catch (MessagingException | org.springframework.mail.MailException e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send OTP email. Please check email credentials or try again later.");
        }
    }

    private String buildOtpEmailHtml(String userName, String otp) {
        String displayName = (userName != null && !userName.isBlank()) ? userName : "User";

        return """
                <!DOCTYPE html>
                <html lang="en">

                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Password Reset Verification</title>
                </head>

                <body style="margin:0;padding:0;background:#f4f6f9;font-family:'Segoe UI',Arial,sans-serif;">

                <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f6f9;padding:40px 15px;">
                <tr>
                <td align="center">

                <table width="620" cellpadding="0" cellspacing="0"
                style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 30px rgba(0,0,0,.08);">

                <!-- ================= HEADER ================= -->

                <tr>
                <td align="center"
                style="padding:45px 20px;
                background:linear-gradient(90deg,#2CCB63 0%,#00A651 100%);">

                <h1 style="
                margin:0;
                font-size:34px;
                font-weight:700;
                color:#ffffff;
                letter-spacing:0.5px;">
                Whatsup Market Place
                </h1>

                </td>
                </tr>

                <!-- ================= BODY ================= -->

                <tr>
                <td style="padding:45px;">

                <h2 style="
                margin-top:0;
                font-size:28px;
                font-weight:600;
                color:#202124;">
                Reset Your Password
                </h2>

                <p style="
                font-size:16px;
                line-height:30px;
                color:#5f6368;">

                Hello <strong>{{USER_NAME}}</strong>,

                </p>

                <p style="
                font-size:16px;
                line-height:30px;
                color:#5f6368;">

                We received a request to reset the password for your
                <strong>Whatsup Market Place</strong> account.

                To continue, please verify your identity using the One-Time Password (OTP) below.

                If you initiated this request, enter the verification code on the password reset page.

                If you didn't request a password reset, you can safely ignore this email. No changes will be made to your account unless this verification code is used.

                </p>

                <!-- OTP -->

                <table width="100%" cellpadding="0" cellspacing="0" style="margin:40px 0;">
                <tr>
                <td align="center">

                <div style="
                display:inline-block;
                padding:28px 45px;
                background:#F8FFF9;
                border:2px dashed #00A651;
                border-radius:16px;">

                <div style="
                font-size:14px;
                color:#666;
                margin-bottom:10px;">
                Verification Code
                </div>

                <div style="
                font-size:42px;
                font-weight:700;
                letter-spacing:10px;
                color:#00A651;">
                {{OTP}}
                </div>

                <div style="
                margin-top:12px;
                font-size:14px;
                color:#666;">
                Valid for 5 Minutes
                </div>

                </div>

                </td>
                </tr>
                </table>

                <!-- Security Notice -->

                <table width="100%" cellpadding="0" cellspacing="0"
                style="background:#F9FAFB;border-left:5px solid #00A651;border-radius:10px;">

                <tr>
                <td style="padding:22px;">

                <h3 style="
                margin:0;
                font-size:18px;
                color:#202124;">
                Security Notice
                </h3>

                <p style="
                margin:12px 0 0;
                font-size:15px;
                line-height:28px;
                color:#5f6368;">

                For your protection, Whatsup Market Place will never ask for your password or verification code via email, phone, or message.

                Please keep this code confidential and do not share it with anyone.

                </p>

                </td>
                </tr>
                </table>

                <p style="
                margin-top:35px;
                font-size:15px;
                line-height:28px;
                color:#5f6368;">

                If you're having trouble accessing your account, our support team is here to help.

                </p>

                <p style="
                margin-top:25px;
                font-size:15px;
                color:#202124;">

                Regards,<br>
                <strong>Whatsup Market Place Security Team</strong>

                </p>

                </td>
                </tr>

                <!-- ================= FOOTER ================= -->

                <tr>
                <td align="center"
                style="
                padding:35px;
                background:linear-gradient(90deg,#2CCB63 0%,#00A651 100%);">

                <h2 style="
                margin:0;
                font-size:24px;
                font-weight:600;
                color:#ffffff;">
                Whatsup Market Place
                </h2>

                <p style="
                margin:15px 0;
                font-size:15px;
                line-height:26px;
                color:#E9FFF1;">
                Helping businesses grow through innovative digital marketing solutions.
                </p>

                <p style="
                margin:5px 0;
                font-size:14px;
                color:#F5FFF7;">
                support@whatsupmarketplace.com
                </p>

                <p style="
                margin:5px 0;
                font-size:14px;
                color:#F5FFF7;">
                www.whatsupmarketplace.com
                </p>

                <hr style="
                margin:25px 0;
                border:none;
                border-top:1px solid rgba(255,255,255,.25);">

                <p style="
                margin:0;
                font-size:13px;
                line-height:24px;
                color:#E8FFF0;">

                © 2026 Whatsup Marketplace. All rights reserved.

                <br><br>

                This is an automated security email. Please do not reply to this message.

                </p>

                </td>
                </tr>

                </table>

                </td>
                </tr>
                </table>

                </body>
                </html>
                """
                .replace("{{USER_NAME}}", displayName)
                .replace("{{OTP}}", otp);
    }
}
