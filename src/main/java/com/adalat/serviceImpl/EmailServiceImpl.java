package com.adalat.serviceImpl;

import com.adalat.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.properties.mail.smtp.from:support@awaazmanki.com}")
    private String fromEmail;

    @Override
    @Async
    public void sendVerificationEmail(String to, String name, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Adalat - Verify Your Email Address");

            ClassPathResource resource = new ClassPathResource("templates/email/email-verification.html");
            String htmlTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            String htmlContent = htmlTemplate.replace("{name}", name != null ? name : "User")
                                             .replace("{OTP}", otp);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent successfully to {}", to);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}", to, e);
        }
    }

    @Override
    @Async
    public void sendEmailChangeOtp(String to, String name, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Adalat Advocate Portal - Verify Your New Email Address");

            ClassPathResource resource = new ClassPathResource("templates/email/email-change-otp.html");
            String htmlTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            String htmlContent = htmlTemplate.replace("{name}", name != null ? name : "Advocate")
                                             .replace("{OTP}", otp);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email change OTP sent successfully to {}", to);

        } catch (Exception e) {
            log.error("Failed to send email change OTP to {}", to, e);
        }
    }

    @Override
    @Async
    public void sendPasswordChangeAlert(String to, String name) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Security Alert: Your Adalat Advocate Password Was Changed");

            ClassPathResource resource = new ClassPathResource("templates/email/password-change-alert.html");
            String htmlTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            String formattedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            String htmlContent = htmlTemplate.replace("{name}", name != null ? name : "Advocate")
                                             .replace("{timestamp}", formattedTime);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password change alert email sent successfully to {}", to);

        } catch (Exception e) {
            log.error("Failed to send password change alert to {}", to, e);
        }
    }
}
