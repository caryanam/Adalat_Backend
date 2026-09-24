package com.adalat.serviceImpl;

import com.adalat.dto.OtpResponseDTO;
import com.adalat.dto.ResendOtpRequestDTO;
import com.adalat.dto.VerifyOtpRequestDTO;
import com.adalat.entity.Customer;
import com.adalat.entity.EmailOtp;
import com.adalat.entity.Lawyer;
import com.adalat.enums.Role;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.EmailOtpRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.service.EmailOtpService;
import com.adalat.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpServiceImpl implements EmailOtpService {

    private final EmailOtpRepository emailOtpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_MINUTES = 2;
    private static final int MAX_ATTEMPTS = 5;

    @Override
    @Transactional
    public void generateAndSendOtp(String email, Role role, String name) {
        String cleanEmail = email.trim().toLowerCase();
        String otp = generateNumericOtp(OTP_LENGTH);
        String otpHash = otp; 

        LocalDateTime now = LocalDateTime.now();
        
        EmailOtp emailOtp = EmailOtp.builder()
                .email(cleanEmail)
                .role(role != null ? role : Role.LAWYER)
                .otpHash(otpHash)
                .expiresAt(now.plusMinutes(OTP_VALIDITY_MINUTES))
                .resendAvailableAt(now.plusMinutes(RESEND_COOLDOWN_MINUTES))
                .attemptCount(0)
                .used(false)
                .build();

        emailOtpRepository.save(emailOtp);

        // Send email
        emailService.sendVerificationEmail(cleanEmail, name != null ? name : "User", otp);
        log.info("OTP generated and email queued for: {}", cleanEmail);
    }

    @Override
    @Transactional
    public OtpResponseDTO verifyOtp(VerifyOtpRequestDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(cleanEmail, request.getRole());

        if (optionalOtp.isEmpty()) {
            optionalOtp = emailOtpRepository.findTopByEmailOrderByCreatedAtDesc(cleanEmail);
        }

        if (optionalOtp.isEmpty()) {
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("No OTP request found for this email.")
                    .build();
        }

        EmailOtp emailOtp = optionalOtp.get();

        if (emailOtp.getUsed()) {
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("This OTP has already been used.")
                    .build();
        }

        if (LocalDateTime.now().isAfter(emailOtp.getExpiresAt())) {
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("OTP has expired. Please request a new OTP.")
                    .build();
        }

        if (emailOtp.getAttemptCount() >= MAX_ATTEMPTS) {
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("Maximum OTP attempts exceeded. Please request a new OTP.")
                    .build();
        }

        // Compare plain text OTP
        if (!request.getOtp().equals(emailOtp.getOtpHash())) {
            emailOtp.setAttemptCount(emailOtp.getAttemptCount() + 1);
            emailOtpRepository.save(emailOtp);
            
            if (emailOtp.getAttemptCount() >= MAX_ATTEMPTS) {
                return OtpResponseDTO.builder()
                        .success(false)
                        .message("Maximum OTP attempts exceeded. Please request a new OTP.")
                        .build();
            }
            
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("Invalid OTP. Please try again.")
                    .build();
        }

        // OTP is valid
        emailOtp.setUsed(true);
        emailOtp.setVerifiedAt(LocalDateTime.now());
        emailOtpRepository.save(emailOtp);

        // Update user entity if they exist
        Optional<Customer> optionalCustomer = customerRepository.findByEmail(cleanEmail);
        if (optionalCustomer.isPresent()) {
            Customer customer = optionalCustomer.get();
            customer.setEmailVerified(true);
            customer.setEmailVerifiedAt(LocalDateTime.now());
            customerRepository.save(customer);
        }

        Optional<Lawyer> optionalLawyer = lawyerRepository.findByEmail(cleanEmail);
        if (optionalLawyer.isPresent()) {
            Lawyer lawyer = optionalLawyer.get();
            lawyer.setEmailVerified(true);
            lawyer.setEmailVerifiedAt(LocalDateTime.now());
            lawyerRepository.save(lawyer);
        }

        return OtpResponseDTO.builder()
                .success(true)
                .message("Email verified successfully.")
                .emailVerified(true)
                .build();
    }

    @Override
    @Transactional
    public OtpResponseDTO resendOtp(ResendOtpRequestDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        Role role = request.getRole();
        String name = "User";

        Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
        Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);

        if (lawyerOpt.isPresent()) {
            role = Role.LAWYER;
            name = lawyerOpt.get().getFullName();
        } else if (customerOpt.isPresent()) {
            role = Role.CUSTOMER;
            name = customerOpt.get().getFullName();
        }

        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailOrderByCreatedAtDesc(cleanEmail);

        if (optionalOtp.isPresent()) {
            EmailOtp latestOtp = optionalOtp.get();
            if (!latestOtp.getUsed() && LocalDateTime.now().isBefore(latestOtp.getResendAvailableAt())) {
                long retryAfterSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), latestOtp.getResendAvailableAt());
                return OtpResponseDTO.builder()
                        .success(false)
                        .message("Please wait before requesting a new OTP.")
                        .retryAfterSeconds(retryAfterSeconds > 0 ? retryAfterSeconds : 1)
                        .build();
            }
            // Invalidate the old OTP so it cannot be used
            latestOtp.setUsed(true);
            emailOtpRepository.save(latestOtp);
        }

        // Generate and send new OTP
        generateAndSendOtp(cleanEmail, role != null ? role : Role.LAWYER, name);

        return OtpResponseDTO.builder()
                .success(true)
                .message("A new OTP has been sent to your email.")
                .resendAvailableAfterSeconds((long) (RESEND_COOLDOWN_MINUTES * 60))
                .otpExpiresAfterSeconds((long) (OTP_VALIDITY_MINUTES * 60))
                .build();
    }

    @Override
    public OtpResponseDTO getOtpStatus(String email, Role role) {
        String cleanEmail = email.trim().toLowerCase();
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(cleanEmail, role);
        if (optionalOtp.isEmpty()) {
            optionalOtp = emailOtpRepository.findTopByEmailOrderByCreatedAtDesc(cleanEmail);
        }
        
        if (optionalOtp.isEmpty() || optionalOtp.get().getUsed()) {
            return OtpResponseDTO.builder()
                    .success(false)
                    .message("No active OTP found.")
                    .build();
        }
        
        EmailOtp latestOtp = optionalOtp.get();
        long expiresAfter = ChronoUnit.SECONDS.between(LocalDateTime.now(), latestOtp.getExpiresAt());
        long resendAfter = ChronoUnit.SECONDS.between(LocalDateTime.now(), latestOtp.getResendAvailableAt());
        
        return OtpResponseDTO.builder()
                .success(true)
                .message("OTP status retrieved.")
                .otpExpiresAfterSeconds(expiresAfter > 0 ? expiresAfter : 0)
                .resendAvailableAfterSeconds(resendAfter > 0 ? resendAfter : 0)
                .build();
    }

    @Override
    public boolean isEmailVerified(String email, Role role) {
        if (email == null) return false;
        String cleanEmail = email.trim().toLowerCase();

        // 1. Check if ANY verified OTP exists for this email within the last 30 minutes
        Optional<EmailOtp> verifiedOtp = emailOtpRepository.findTopByEmailAndUsedTrueOrderByVerifiedAtDesc(cleanEmail);
        if (verifiedOtp.isPresent() && verifiedOtp.get().getVerifiedAt() != null) {
            if (verifiedOtp.get().getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(30))) {
                return true;
            }
        }

        // 2. Check by role if specified
        if (role != null) {
            Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(cleanEmail, role);
            if (optionalOtp.isPresent() && Boolean.TRUE.equals(optionalOtp.get().getUsed()) && optionalOtp.get().getVerifiedAt() != null) {
                if (optionalOtp.get().getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(30))) {
                    return true;
                }
            }
        }

        // 3. Fallback: Check latest OTP overall
        Optional<EmailOtp> anyOtp = emailOtpRepository.findTopByEmailOrderByCreatedAtDesc(cleanEmail);
        if (anyOtp.isPresent() && Boolean.TRUE.equals(anyOtp.get().getUsed()) && anyOtp.get().getVerifiedAt() != null) {
            if (anyOtp.get().getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(30))) {
                return true;
            }
        }

        // 4. Fallback: Check if user entity itself is marked emailVerified
        Optional<Customer> customer = customerRepository.findByEmail(cleanEmail);
        if (customer.isPresent() && Boolean.TRUE.equals(customer.get().getEmailVerified())) {
            return true;
        }

        Optional<Lawyer> lawyer = lawyerRepository.findByEmail(cleanEmail);
        if (lawyer.isPresent() && Boolean.TRUE.equals(lawyer.get().getEmailVerified())) {
            return true;
        }

        return false;
    }

    private String generateNumericOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
