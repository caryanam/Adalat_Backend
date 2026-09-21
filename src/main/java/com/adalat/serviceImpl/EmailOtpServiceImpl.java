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
        // Find existing valid OTPs and invalidate them (or just rely on sorting)
        // To be safe, we just create a new one and fetch the latest.

        String otp = generateNumericOtp(OTP_LENGTH);
        // User requested plain OTP storage instead of hash
        String otpHash = otp; 

        LocalDateTime now = LocalDateTime.now();
        
        EmailOtp emailOtp = EmailOtp.builder()
                .email(email)
                .role(role)
                .otpHash(otpHash)
                .expiresAt(now.plusMinutes(OTP_VALIDITY_MINUTES))
                .resendAvailableAt(now.plusMinutes(RESEND_COOLDOWN_MINUTES))
                .attemptCount(0)
                .used(false)
                .build();

        emailOtpRepository.save(emailOtp);

        // Send email
        emailService.sendVerificationEmail(email, name, otp);
        log.info("OTP generated and email queued for: {}", email);
    }

    @Override
    @Transactional
    public OtpResponseDTO verifyOtp(VerifyOtpRequestDTO request) {
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(request.getEmail(), request.getRole());

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
        if (request.getRole() == Role.CUSTOMER) {
            Optional<Customer> optionalCustomer = customerRepository.findByEmail(request.getEmail());
            if (optionalCustomer.isPresent()) {
                Customer customer = optionalCustomer.get();
                customer.setEmailVerified(true);
                customer.setEmailVerifiedAt(LocalDateTime.now());
                customerRepository.save(customer);
            }
        } else if (request.getRole() == Role.LAWYER) {
            Optional<Lawyer> optionalLawyer = lawyerRepository.findByEmail(request.getEmail());
            if (optionalLawyer.isPresent()) {
                Lawyer lawyer = optionalLawyer.get();
                lawyer.setEmailVerified(true);
                lawyer.setEmailVerifiedAt(LocalDateTime.now());
                lawyerRepository.save(lawyer);
            }
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
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(request.getEmail(), request.getRole());
        
        String name = "User"; // Default name
        
        if (request.getRole() == Role.CUSTOMER) {
            Optional<Customer> c = customerRepository.findByEmail(request.getEmail());
            if (c.isPresent()) name = c.get().getFullName();
        } else if (request.getRole() == Role.LAWYER) {
            Optional<Lawyer> l = lawyerRepository.findByEmail(request.getEmail());
            if (l.isPresent()) name = l.get().getFullName();
        }

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
        generateAndSendOtp(request.getEmail(), request.getRole(), name);

        return OtpResponseDTO.builder()
                .success(true)
                .message("A new OTP has been sent to your email.")
                .resendAvailableAfterSeconds((long) (RESEND_COOLDOWN_MINUTES * 60))
                .otpExpiresAfterSeconds((long) (OTP_VALIDITY_MINUTES * 60))
                .build();
    }

    @Override
    public OtpResponseDTO getOtpStatus(String email, Role role) {
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(email, role);
        
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
        Optional<EmailOtp> optionalOtp = emailOtpRepository.findTopByEmailAndRoleOrderByCreatedAtDesc(email, role);
        if (optionalOtp.isPresent() && optionalOtp.get().getUsed() && optionalOtp.get().getVerifiedAt() != null) {
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
