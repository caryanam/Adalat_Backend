package com.adalat.serviceImpl;

import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.entity.Admin;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
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
    public void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword) {
        String cleanEmail = email.trim().toLowerCase();

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        if (!emailOtpService.isEmailVerified(cleanEmail, null)) {
            throw new IllegalArgumentException("Please verify the OTP sent to your registered email before resetting password.");
        }

        // 1. Try Customer
        Optional<Customer> customerOpt = customerRepository.findByEmail(cleanEmail);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setPassword(passwordEncoder.encode(newPassword));
            customerRepository.save(customer);
            emailService.sendPasswordChangeAlert(customer.getEmail(), customer.getFullName());
            log.info("Password reset successfully for customer: {}", cleanEmail);
            return;
        }

        // 2. Try Lawyer
        Optional<Lawyer> lawyerOpt = lawyerRepository.findByEmail(cleanEmail);
        if (lawyerOpt.isPresent()) {
            Lawyer lawyer = lawyerOpt.get();
            lawyer.setPassword(passwordEncoder.encode(newPassword));
            lawyerRepository.save(lawyer);
            emailService.sendPasswordChangeAlert(lawyer.getEmail(), lawyer.getFullName());
            log.info("Password reset successfully for lawyer: {}", cleanEmail);
            return;
        }

        // 3. Try Admin
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
