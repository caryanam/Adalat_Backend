package com.adalat.serviceImpl;

import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.ForgotPasswordOtpRequestDTO;
import com.adalat.dto.ForgotPasswordResetDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.dto.OtpResponseDTO;
import com.adalat.entity.Admin;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.enums.Role;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.AdminRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.security.CustomUserDetails;
import com.adalat.security.JwtService;
import com.adalat.service.AuthService;
import com.adalat.service.EmailOtpService;
import com.adalat.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpService emailOtpService;
    private final EmailService emailService;

    @Override
    public AuthResponseDTO authenticate(LoginRequestDTO loginRequest) {
        // This will authenticate using either email or phone, based on what CustomUserDetailsService supports
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getIdentifier(), loginRequest.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        
        // Generate token
        String token = jwtService.generateAccessToken(userDetails);

        return AuthResponseDTO.builder()
                .token(token)
                .name(userDetails.getName())
                .role(userDetails.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public OtpResponseDTO sendForgotPasswordOtp(ForgotPasswordOtpRequestDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        Role targetRole = request.getRole();

        String foundName = null;
        Role foundRole = null;

        if (targetRole != null) {
            if (targetRole == Role.LAWYER) {
                Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
                if (lawyerOpt.isPresent()) {
                    foundRole = Role.LAWYER;
                    foundName = lawyerOpt.get().getFullName();
                } else {
                    throw new ResourceNotFoundException("No advocate account found with email: " + cleanEmail);
                }
            } else if (targetRole == Role.CUSTOMER) {
                Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);
                if (customerOpt.isPresent()) {
                    foundRole = Role.CUSTOMER;
                    foundName = customerOpt.get().getFullName();
                } else {
                    throw new ResourceNotFoundException("No customer account found with email: " + cleanEmail);
                }
            } else if (targetRole == Role.ADMIN) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(cleanEmail);
                if (adminOpt.isPresent()) {
                    foundRole = Role.ADMIN;
                    foundName = adminOpt.get().getFullName();
                } else {
                    throw new ResourceNotFoundException("No admin account found with email: " + cleanEmail);
                }
            }
        }

        if (foundRole == null) {
            // Auto-detect role across all tables (check Lawyer first, then Customer, then Admin)
            Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
            if (lawyerOpt.isPresent()) {
                foundRole = Role.LAWYER;
                foundName = lawyerOpt.get().getFullName();
            } else {
                Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);
                if (customerOpt.isPresent()) {
                    foundRole = Role.CUSTOMER;
                    foundName = customerOpt.get().getFullName();
                } else {
                    Optional<Admin> adminOpt = adminRepository.findByEmail(cleanEmail);
                    if (adminOpt.isPresent()) {
                        foundRole = Role.ADMIN;
                        foundName = adminOpt.get().getFullName();
                    }
                }
            }
        }

        if (foundRole == null) {
            throw new ResourceNotFoundException("No registered account found with email: " + cleanEmail);
        }

        emailOtpService.generateAndSendOtp(cleanEmail, foundRole, foundName);

        return OtpResponseDTO.builder()
                .success(true)
                .message("Verification code sent to " + cleanEmail)
                .otpExpiresAfterSeconds(300L)
                .resendAvailableAfterSeconds(120L)
                .build();
    }

    @Override
    @Transactional
    public void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword) {
        resetPasswordWithEmailOtp(ForgotPasswordResetDTO.builder()
                .email(email)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build());
    }

    @Override
    @Transactional
    public void resetPasswordWithEmailOtp(ForgotPasswordResetDTO request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();
        String roleStr = request.getRole();

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        if (!emailOtpService.isEmailVerified(cleanEmail, null)) {
            throw new IllegalArgumentException("Please verify the OTP sent to your registered email before resetting password.");
        }

        // 1. If role was explicitly specified, prioritize that role
        if (roleStr != null && !roleStr.isBlank()) {
            if ("LAWYER".equalsIgnoreCase(roleStr)) {
                Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
                if (lawyerOpt.isPresent()) {
                    Lawyer lawyer = lawyerOpt.get();
                    lawyer.setPassword(passwordEncoder.encode(newPassword));
                    lawyerRepository.save(lawyer);
                    emailService.sendPasswordChangeAlert(lawyer.getEmail(), lawyer.getFullName());
                    log.info("Password reset successfully for advocate: {}", cleanEmail);
                    return;
                } else {
                    throw new ResourceNotFoundException("No advocate account found with email: " + cleanEmail);
                }
            } else if ("CUSTOMER".equalsIgnoreCase(roleStr)) {
                Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);
                if (customerOpt.isPresent()) {
                    Customer customer = customerOpt.get();
                    customer.setPassword(passwordEncoder.encode(newPassword));
                    customerRepository.save(customer);
                    emailService.sendPasswordChangeAlert(customer.getEmail(), customer.getFullName());
                    log.info("Password reset successfully for customer: {}", cleanEmail);
                    return;
                } else {
                    throw new ResourceNotFoundException("No customer account found with email: " + cleanEmail);
                }
            } else if ("ADMIN".equalsIgnoreCase(roleStr)) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(cleanEmail);
                if (adminOpt.isPresent()) {
                    Admin admin = adminOpt.get();
                    admin.setPassword(passwordEncoder.encode(newPassword));
                    adminRepository.save(admin);
                    emailService.sendPasswordChangeAlert(admin.getEmail(), admin.getFullName());
                    log.info("Password reset successfully for admin: {}", cleanEmail);
                    return;
                } else {
                    throw new ResourceNotFoundException("No admin account found with email: " + cleanEmail);
                }
            }
        }

        // 2. Auto-detect role: check Lawyer, then Customer, then Admin
        Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
        if (lawyerOpt.isPresent()) {
            Lawyer lawyer = lawyerOpt.get();
            lawyer.setPassword(passwordEncoder.encode(newPassword));
            lawyerRepository.save(lawyer);
            emailService.sendPasswordChangeAlert(lawyer.getEmail(), lawyer.getFullName());
            log.info("Password reset successfully for advocate: {}", cleanEmail);
            return;
        }

        Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setPassword(passwordEncoder.encode(newPassword));
            customerRepository.save(customer);
            emailService.sendPasswordChangeAlert(customer.getEmail(), customer.getFullName());
            log.info("Password reset successfully for customer: {}", cleanEmail);
            return;
        }

        Optional<Admin> adminOpt = adminRepository.findByEmail(cleanEmail);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            admin.setPassword(passwordEncoder.encode(newPassword));
            adminRepository.save(admin);
            emailService.sendPasswordChangeAlert(admin.getEmail(), admin.getFullName());
            log.info("Password reset successfully for admin: {}", cleanEmail);
            return;
        }

        throw new ResourceNotFoundException("No registered account found with email: " + cleanEmail);
    }
}
