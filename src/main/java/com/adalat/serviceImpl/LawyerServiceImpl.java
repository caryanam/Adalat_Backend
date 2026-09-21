package com.adalat.serviceImpl;

import com.adalat.dto.*;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.EmailOtp;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.*;
import com.adalat.exception.DuplicateResourceException;
import com.adalat.exception.LawyerNotApprovedException;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.EmailOtpRepository;
import com.adalat.repository.LawyerDocumentRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.PaymentTransactionRepository;
import com.adalat.security.CustomUserDetails;
import com.adalat.security.JwtService;
import com.adalat.service.EmailOtpService;
import com.adalat.service.EmailService;
import com.adalat.service.FileStorageService;
import com.adalat.service.LawyerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawyerServiceImpl implements LawyerService {

    private final LawyerRepository lawyerRepository;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final CustomerRepository customerRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailOtpService emailOtpService;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;

    // ─── STEP 1 — Account ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO registerStep1(LawyerAccountRequestDTO request) {

        if (lawyerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("A lawyer with this email already exists.");
        }
        if (lawyerRepository.existsByMobileNumber(request.getMobileNumber())) {
            throw new DuplicateResourceException("A lawyer with this mobile number already exists.");
        }

        Lawyer lawyer = Lawyer.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobileNumber(request.getMobileNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.LAWYER)
                .registrationStatus(RegistrationStatus.DRAFT)
                .verificationStatus(VerificationStatus.PENDING)
                .accountStatus(AccountStatus.INACTIVE)
                .emailVerified(true)
                .build();

        // Enforce Email Verification inline
        if (!emailOtpService.isEmailVerified(lawyer.getEmail(), Role.LAWYER)) {
            throw new IllegalArgumentException("Please verify your email address before registering.");
        }

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer Step 1 saved: id={}, email={}", saved.getLawyerId(), saved.getEmail());
        
        return toProfileDTO(saved);
    }

    // ─── STEP 2 — Professional ─────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateStep2(Long lawyerId, LawyerProfessionalRequestDTO request) {

        Lawyer lawyer = findLawyerById(lawyerId);
        lawyer.setBarEnrollmentNumber(request.getBarEnrollmentNumber());
        lawyer.setYearsOfExperience(request.getYearsOfExperience());
        lawyer.setEducation(request.getEducation());
        lawyer.setLocation(request.getLocation());
        lawyer.setPracticeAreas(request.getPracticeAreas());
        lawyer.setLanguages(request.getLanguages());
        lawyer.setBio(request.getBio());
        if (request.getProfilePhotoUrl() != null && !request.getProfilePhotoUrl().isBlank()) {
            lawyer.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer Step 2 updated: id={}", lawyerId);
        return toProfileDTO(saved);
    }

    // ─── STEP 4 — Pricing ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateStep4(Long lawyerId, LawyerPricingRequestDTO request) {

        Lawyer lawyer = findLawyerById(lawyerId);
        int fee = 99;

        if (request != null) {
            Object rawAmt = request.getAmount() != null ? request.getAmount() : request.getConsultationRate();
            if (rawAmt != null) {
                String str = rawAmt.toString().trim();
                try {
                    if (str.startsWith("RATE_")) {
                        ConsultationRate r = ConsultationRate.valueOf(str);
                        fee = r.getAmount();
                    } else {
                        fee = (int) Math.round(Double.parseDouble(str.replaceAll("[^0-9.]", "")));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse rate '{}', defaulting to 99", str);
                    fee = 99;
                }
            }
        }

        lawyer.setConsultationFee(fee);
        lawyer.setConsultationRate(ConsultationRate.fromAmount(fee));
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer Step 4 updated: id={}, custom consultation fee=₹{}", lawyerId, fee);
        return toProfileDTO(saved);
    }

    // ─── STEP 5 — UPI ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateStep5(Long lawyerId, LawyerUpiRequestDTO request) {

        Lawyer lawyer = findLawyerById(lawyerId);
        lawyer.setUpiId(request.getUpiId());
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer Step 5 updated: id={}", lawyerId);
        return toProfileDTO(saved);
    }

    // ─── STEP 6 — Submit ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerSubmitResponseDTO submitApplication(Long lawyerId) {

        Lawyer lawyer = findLawyerById(lawyerId);

        // Auto-assign defaults if pricing or upi was not explicitly updated
        if (lawyer.getConsultationRate() == null) {
            lawyer.setConsultationRate(ConsultationRate.RATE_99);
        }
        if (lawyer.getUpiId() == null || lawyer.getUpiId().isBlank()) {
            lawyer.setUpiId("advocate@upi");
        }

        // Validate mandatory steps
        validateSubmission(lawyer);

        lawyer.setRegistrationStatus(RegistrationStatus.SUBMITTED);
        lawyer.setVerificationStatus(VerificationStatus.PENDING);
        lawyer.setAccountStatus(AccountStatus.INACTIVE);
        lawyerRepository.save(lawyer);

        log.info("Lawyer application submitted: id={}", lawyerId);
        return LawyerSubmitResponseDTO.builder()
                .lawyerId(lawyerId)
                .message("Application submitted successfully. Waiting for admin verification.")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
    }

    private void validateSubmission(Lawyer lawyer) {
        StringBuilder errors = new StringBuilder();

        if (lawyer.getBarEnrollmentNumber() == null || lawyer.getBarEnrollmentNumber().isBlank()) {
            errors.append("Professional details (Step 2) are incomplete. ");
        }
        if (lawyer.getPracticeAreas() == null || lawyer.getPracticeAreas().isEmpty()) {
            errors.append("Practice areas are not selected. ");
        }
        if (lawyer.getLanguages() == null || lawyer.getLanguages().isEmpty()) {
            errors.append("Languages are not selected. ");
        }

        // Check at least one document is uploaded
        List<LawyerDocument> docs = lawyerDocumentRepository.findByLawyer(lawyer);
        if (docs.isEmpty()) {
            errors.append("No documents uploaded (Step 3). ");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Cannot submit application: " + errors.toString().trim());
        }
    }

    // ─── LOGIN ─────────────────────────────────────────────────────────────────

    @Override
    public LawyerLoginResponseDTO loginLawyer(LoginRequestDTO request) {

        Lawyer lawyer = lawyerRepository.findByEmail(request.getIdentifier())
                .or(() -> lawyerRepository.findByMobileNumber(request.getIdentifier()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No lawyer account found with this email or mobile number."));

        // Password check
        if (!passwordEncoder.matches(request.getPassword(), lawyer.getPassword())) {
            throw new BadCredentialsException("Invalid password.");
        }

        // Verification gate: only block if explicitly REJECTED by admin
        if (lawyer.getVerificationStatus() == VerificationStatus.REJECTED) {
            String reason = lawyer.getRejectionReason() != null ? ": " + lawyer.getRejectionReason() : ".";
            throw new LawyerNotApprovedException("Your advocate verification was not approved" + reason);
        }

        // Email Verification gate
        if (!Boolean.TRUE.equals(lawyer.getEmailVerified())) {
            throw new IllegalArgumentException("Please verify your email address before logging in.");
        }

        // Generate JWT
        CustomUserDetails userDetails = new CustomUserDetails(
                lawyer.getLawyerId(),
                lawyer.getFullName(),
                lawyer.getEmail(),
                lawyer.getPassword(),
                Role.LAWYER
        );
        String token = jwtService.generateAccessToken(userDetails);

        log.info("Lawyer login successful: id={}, status={}", lawyer.getLawyerId(), lawyer.getRegistrationStatus());

        return LawyerLoginResponseDTO.builder()
                .token(token)
                .lawyer(toProfileDTO(lawyer))
                .build();
    }

    // ─── ADMIN OPERATIONS ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO approveLawyer(Long lawyerId) {

        Lawyer lawyer = findLawyerById(lawyerId);
        lawyer.setVerificationStatus(VerificationStatus.APPROVED);
        lawyer.setAccountStatus(AccountStatus.ACTIVE);
        lawyer.setRejectionReason(null);

        // Synchronize all uploaded documents' verification status in MySQL DB
        if (lawyer.getDocuments() != null && !lawyer.getDocuments().isEmpty()) {
            for (LawyerDocument doc : lawyer.getDocuments()) {
                doc.setVerificationStatus(DocumentVerificationStatus.APPROVED);
            }
        }

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer & documents approved by admin: id={}", lawyerId);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO rejectLawyer(Long lawyerId, AdminRejectRequestDTO request) {

        Lawyer lawyer = findLawyerById(lawyerId);
        lawyer.setVerificationStatus(VerificationStatus.REJECTED);
        lawyer.setAccountStatus(AccountStatus.INACTIVE);
        lawyer.setRejectionReason(request.getRejectionReason());

        // Synchronize all uploaded documents' verification status in MySQL DB
        if (lawyer.getDocuments() != null && !lawyer.getDocuments().isEmpty()) {
            for (LawyerDocument doc : lawyer.getDocuments()) {
                doc.setVerificationStatus(DocumentVerificationStatus.REJECTED);
            }
        }

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer & documents rejected by admin: id={}, reason={}", lawyerId, request.getRejectionReason());
        return toProfileDTO(saved);
    }

    @Override
    public List<LawyerProfileResponseDTO> getPendingLawyers() {
        return lawyerRepository
                .findByRegistrationStatusAndVerificationStatus(
                        RegistrationStatus.SUBMITTED, VerificationStatus.PENDING)
                .stream()
                .map(this::toProfileDTO)
                .collect(Collectors.toList());
    }

    @Override
    public LawyerProfileResponseDTO getLawyerById(Long lawyerId) {
        return toProfileDTO(findLawyerById(lawyerId));
    }

    @Override
    public List<LawyerProfileResponseDTO> getPublicDirectoryLawyers() {
        return lawyerRepository.findAll().stream()
                .filter(l -> l.getRole() == Role.LAWYER)
                .map(this::toProfileDTO)
                .collect(Collectors.toList());
    }

    // ─── PROFILE MANAGEMENT & SECURITY ─────────────────────────────────────────

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateProfile(Long lawyerId, LawyerUpdateProfileRequestDTO request) {
        Lawyer lawyer = findLawyerById(lawyerId);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            lawyer.setFullName(request.getFullName().trim());
        }
        if (request.getMobileNumber() != null && !request.getMobileNumber().isBlank()) {
            String cleanMobile = request.getMobileNumber().trim();
            // Check if another lawyer already has this mobile
            lawyerRepository.findByMobileNumber(cleanMobile)
                    .filter(l -> !l.getLawyerId().equals(lawyerId))
                    .ifPresent(l -> {
                        throw new DuplicateResourceException("This mobile number is already registered by another account.");
                    });
            lawyer.setMobileNumber(cleanMobile);
        }
        if (request.getBarEnrollmentNumber() != null) {
            lawyer.setBarEnrollmentNumber(request.getBarEnrollmentNumber().trim());
        }
        if (request.getYearsOfExperience() != null) {
            lawyer.setYearsOfExperience(request.getYearsOfExperience());
        }
        if (request.getEducation() != null) {
            lawyer.setEducation(request.getEducation().trim());
        }
        if (request.getLocation() != null) {
            lawyer.setLocation(request.getLocation().trim());
        }
        if (request.getPracticeAreas() != null) {
            lawyer.setPracticeAreas(request.getPracticeAreas());
        }
        if (request.getLanguages() != null) {
            lawyer.setLanguages(request.getLanguages());
        }
        if (request.getBio() != null) {
            lawyer.setBio(request.getBio().trim());
        }
        if (request.getConsultationFee() != null) {
            int fee = Math.max(0, request.getConsultationFee());
            lawyer.setConsultationFee(fee);
            lawyer.setConsultationRate(ConsultationRate.fromAmount(fee));
        }
        if (request.getUpiId() != null) {
            lawyer.setUpiId(request.getUpiId().trim());
        }
        if (request.getProfilePhotoUrl() != null && !request.getProfilePhotoUrl().isBlank()) {
            lawyer.setProfilePhotoUrl(request.getProfilePhotoUrl().trim());
        }

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer profile updated successfully: lawyerId={}", lawyerId);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateProfilePhoto(Long lawyerId, MultipartFile file) {
        Lawyer lawyer = findLawyerById(lawyerId);
        try {
            String fileUrl = fileStorageService.storeFile(lawyerId, file);
            lawyer.setProfilePhotoUrl(fileUrl);
            Lawyer saved = lawyerRepository.save(lawyer);
            log.info("Lawyer profile photo updated: lawyerId={}, url={}", lawyerId, fileUrl);
            return toProfileDTO(saved);
        } catch (IOException e) {
            log.error("Failed to store profile photo for lawyerId={}: {}", lawyerId, e.getMessage());
            throw new RuntimeException("Failed to upload profile photo: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void sendEmailChangeOtp(Long lawyerId, String newEmail) {
        Lawyer lawyer = findLawyerById(lawyerId);
        String cleanEmail = newEmail.trim().toLowerCase();

        if (cleanEmail.equalsIgnoreCase(lawyer.getEmail())) {
            throw new IllegalArgumentException("The new email address cannot be the same as your current email.");
        }

        // Check if email is already used by another lawyer or customer
        lawyerRepository.findByEmail(cleanEmail)
                .filter(l -> !l.getLawyerId().equals(lawyerId))
                .ifPresent(l -> {
                    throw new DuplicateResourceException("This email address is already registered to another advocate.");
                });

        customerRepository.findByEmail(cleanEmail).ifPresent(c -> {
            throw new DuplicateResourceException("This email address is already registered to a customer account.");
        });

        // Generate 6 digit OTP
        String otp = generateNumericOtp(OTP_LENGTH);
        LocalDateTime now = LocalDateTime.now();

        EmailOtp emailOtp = EmailOtp.builder()
                .email(cleanEmail)
                .role(Role.LAWYER)
                .otpHash(otp)
                .expiresAt(now.plusMinutes(OTP_VALIDITY_MINUTES))
                .resendAvailableAt(now.plusMinutes(1))
                .attemptCount(0)
                .used(false)
                .build();

        emailOtpRepository.save(emailOtp);

        // Dispatch OTP email
        emailService.sendEmailChangeOtp(cleanEmail, lawyer.getFullName(), otp);
        log.info("Email change OTP generated and sent to: {}", cleanEmail);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO verifyAndUpdateEmail(Long lawyerId, String newEmail, String otp) {
        Lawyer lawyer = findLawyerById(lawyerId);
        String cleanEmail = newEmail.trim().toLowerCase();

        Optional<EmailOtp> optionalOtp = emailOtpRepository
                .findTopByEmailAndRoleOrderByCreatedAtDesc(cleanEmail, Role.LAWYER);

        if (optionalOtp.isEmpty()) {
            throw new IllegalArgumentException("No active OTP request found for " + cleanEmail);
        }

        EmailOtp emailOtp = optionalOtp.get();

        if (Boolean.TRUE.equals(emailOtp.getUsed())) {
            throw new IllegalArgumentException("This OTP has already been used. Please request a new OTP.");
        }

        if (LocalDateTime.now().isAfter(emailOtp.getExpiresAt())) {
            throw new IllegalArgumentException("OTP has expired. Please request a new OTP.");
        }

        if (emailOtp.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            throw new IllegalArgumentException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        if (!otp.trim().equals(emailOtp.getOtpHash())) {
            emailOtp.setAttemptCount(emailOtp.getAttemptCount() + 1);
            emailOtpRepository.save(emailOtp);
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }

        // Mark OTP as used
        emailOtp.setUsed(true);
        emailOtp.setVerifiedAt(LocalDateTime.now());
        emailOtpRepository.save(emailOtp);

        // Update lawyer email
        lawyer.setEmail(cleanEmail);
        lawyer.setEmailVerified(true);
        lawyer.setEmailVerifiedAt(LocalDateTime.now());

        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer email successfully changed: lawyerId={}, newEmail={}", lawyerId, cleanEmail);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public void changePassword(Long lawyerId, LawyerChangePasswordRequestDTO request) {
        Lawyer lawyer = findLawyerById(lawyerId);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        // Verify either current password OR verified email OTP
        if (request.getCurrentPassword() != null && !request.getCurrentPassword().isBlank()) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), lawyer.getPassword())) {
                throw new BadCredentialsException("Current password is incorrect.");
            }
        } else {
            if (!emailOtpService.isEmailVerified(lawyer.getEmail(), Role.LAWYER)) {
                throw new IllegalArgumentException("Please verify the OTP sent to your registered email before setting your new password.");
            }
        }

        if (request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        // Update password in DB
        lawyer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        lawyerRepository.save(lawyer);

        // Dispatch security alert email
        emailService.sendPasswordChangeAlert(lawyer.getEmail(), lawyer.getFullName());
        log.info("Lawyer password changed successfully and security alert email dispatched: lawyerId={}", lawyerId);
    }

    @Override
    @Transactional
    public void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword) {
        String cleanEmail = email.trim().toLowerCase();
        Lawyer lawyer = lawyerRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No advocate account found with email: " + cleanEmail));

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        if (!emailOtpService.isEmailVerified(cleanEmail, Role.LAWYER)) {
            throw new IllegalArgumentException("Please verify the OTP sent to your registered email before resetting password.");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        lawyer.setPassword(passwordEncoder.encode(newPassword));
        lawyerRepository.save(lawyer);

        emailService.sendPasswordChangeAlert(lawyer.getEmail(), lawyer.getFullName());
        log.info("Lawyer password reset via email OTP successfully: email={}", cleanEmail);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updatePricingFee(Long lawyerId, Integer consultationFee) {
        Lawyer lawyer = findLawyerById(lawyerId);
        int fee = (consultationFee != null && consultationFee >= 0) ? consultationFee : 99;
        lawyer.setConsultationFee(fee);
        lawyer.setConsultationRate(ConsultationRate.fromAmount(fee));
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer pricing updated: lawyerId={}, fee=₹{}", lawyerId, fee);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updatePracticeAreas(Long lawyerId, Set<PracticeArea> practiceAreas) {
        Lawyer lawyer = findLawyerById(lawyerId);
        if (practiceAreas != null && !practiceAreas.isEmpty()) {
            lawyer.setPracticeAreas(practiceAreas);
        }
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer practice areas updated: lawyerId={}, areasCount={}", lawyerId, practiceAreas != null ? practiceAreas.size() : 0);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateLanguages(Long lawyerId, Set<Language> languages) {
        Lawyer lawyer = findLawyerById(lawyerId);
        if (languages != null && !languages.isEmpty()) {
            lawyer.setLanguages(languages);
        }
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer languages updated: lawyerId={}, languagesCount={}", lawyerId, languages != null ? languages.size() : 0);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateUpiId(Long lawyerId, String upiId) {
        Lawyer lawyer = findLawyerById(lawyerId);
        if (upiId != null && !upiId.isBlank()) {
            lawyer.setUpiId(upiId.trim());
        }
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer UPI ID updated: lawyerId={}, upi={}", lawyerId, upiId);
        return toProfileDTO(saved);
    }

    @Override
    @Transactional
    public LawyerProfileResponseDTO updateBio(Long lawyerId, String bio) {
        Lawyer lawyer = findLawyerById(lawyerId);
        if (bio != null) {
            lawyer.setBio(bio.trim());
        }
        Lawyer saved = lawyerRepository.save(lawyer);
        log.info("Lawyer Bio updated: lawyerId={}", lawyerId);
        return toProfileDTO(saved);
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        }
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter.");
        }
        if (!Pattern.compile("[0-9]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one number.");
        }
        if (!Pattern.compile("[^a-zA-Z0-9]").matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one special character.");
        }
    }

    private String generateNumericOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    private Lawyer findLawyerById(Long lawyerId) {
        return lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lawyer not found with ID: " + lawyerId));
    }

    public LawyerProfileResponseDTO toProfileDTO(Lawyer lawyer) {
        List<LawyerDocumentResponseDTO> docDTOs = lawyerDocumentRepository.findByLawyer(lawyer)
                .stream()
                .map(doc -> LawyerDocumentResponseDTO.builder()
                        .id(doc.getId())
                        .documentType(doc.getDocumentType())
                        .originalFileName(doc.getOriginalFileName())
                        .fileUrl(doc.getFileUrl())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .verificationStatus(doc.getVerificationStatus())
                        .uploadedAt(doc.getUploadedAt())
                        .build())
                .collect(Collectors.toList());

        Integer finalFee = lawyer.getConsultationFee() != null ? lawyer.getConsultationFee()
                : (lawyer.getConsultationRate() != null ? lawyer.getConsultationRate().getAmount() : 99);

        return LawyerProfileResponseDTO.builder()
                .lawyerId(lawyer.getLawyerId())
                .fullName(lawyer.getFullName())
                .email(lawyer.getEmail())
                .mobileNumber(lawyer.getMobileNumber())
                .barEnrollmentNumber(lawyer.getBarEnrollmentNumber())
                .yearsOfExperience(lawyer.getYearsOfExperience())
                .education(lawyer.getEducation())
                .location(lawyer.getLocation())
                .practiceAreas(lawyer.getPracticeAreas())
                .languages(lawyer.getLanguages())
                .bio(lawyer.getBio())
                .consultationRate(lawyer.getConsultationRate())
                .consultationRateAmount(finalFee)
                .consultationFee(finalFee)
                .upiId(lawyer.getUpiId())
                .role(lawyer.getRole())
                .registrationStatus(lawyer.getRegistrationStatus())
                .verificationStatus(lawyer.getVerificationStatus())
                .accountStatus(lawyer.getAccountStatus())
                .available(lawyer.getAvailable() != null ? lawyer.getAvailable() : true)
                .rating(lawyer.getRating() != null ? lawyer.getRating() : 0.0)
                .totalConsultations(lawyer.getTotalConsultations() != null ? lawyer.getTotalConsultations() : 0)
                .profilePhotoUrl(lawyer.getProfilePhotoUrl())
                .rejectionReason(lawyer.getRejectionReason())
                .documents(docDTOs)
                .createdAt(lawyer.getCreatedAt())
                .updatedAt(lawyer.getUpdatedAt())
                .build();
    }

    @Override
    public LawyerEarningsResponseDTO getLawyerEarnings(Long lawyerId) {
        Lawyer lawyer = findLawyerById(lawyerId);

        // Fetch all payment transactions for this lawyer
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByLawyerId(lawyer.getLawyerId());

        // Also fetch all consultation requests for this lawyer
        List<ConsultationRequest> consultationRequests = consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        List<LawyerTransactionDTO> txDTOs = new ArrayList<>();
        Set<Long> processedRequestIds = new HashSet<>();
        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal todayEarnings = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        // 1. Process existing PaymentTransaction rows
        for (PaymentTransaction pt : transactions) {
            if (pt.getConsultationRequest() != null) {
                processedRequestIds.add(pt.getConsultationRequest().getId());
            }

            BigDecimal amt = pt.getAmount() != null ? pt.getAmount() : new BigDecimal("99.00");
            if (pt.getStatus() == PaymentStatus.PAID) {
                totalEarnings = totalEarnings.add(amt);
                if (pt.getCreatedAt() != null && pt.getCreatedAt().toLocalDate().isEqual(today)) {
                    todayEarnings = todayEarnings.add(amt);
                }
            }

            String customerName = "Registered Client";
            String customerEmail = "";
            String customerPhone = "";
            String category = "LEGAL_CONSULTATION";
            Long reqId = null;

            if (pt.getCustomer() != null) {
                customerName = pt.getCustomer().getFullName();
                customerEmail = pt.getCustomer().getEmail();
                customerPhone = pt.getCustomer().getMobileNumber();
            }

            if (pt.getConsultationRequest() != null) {
                reqId = pt.getConsultationRequest().getId();
                if (pt.getConsultationRequest().getCustomer() != null) {
                    customerName = pt.getConsultationRequest().getCustomer().getFullName();
                    customerEmail = pt.getConsultationRequest().getCustomer().getEmail();
                    customerPhone = pt.getConsultationRequest().getCustomer().getMobileNumber();
                }
                if (pt.getConsultationRequest().getCategory() != null) {
                    category = pt.getConsultationRequest().getCategory().name();
                }
            }

            String dateStr = pt.getCreatedAt() != null ? pt.getCreatedAt().format(formatter) : "Today";
            String rawDateStr = pt.getCreatedAt() != null ? pt.getCreatedAt().toString() : "";

            txDTOs.add(LawyerTransactionDTO.builder()
                    .id(pt.getOrderId() != null ? pt.getOrderId() : ("TXN-" + pt.getId()))
                    .requestId(reqId)
                    .customerName(customerName)
                    .customerEmail(customerEmail)
                    .customerPhone(customerPhone)
                    .amount("₹" + amt.setScale(2, RoundingMode.HALF_UP).toString())
                    .amountNum(amt)
                    .date(dateStr)
                    .rawDate(rawDateStr)
                    .duration("Consultation Session")
                    .status(pt.getStatus() != null ? pt.getStatus().name() : "PAID")
                    .paymentType(pt.getPaymentType() != null ? pt.getPaymentType() : "CONSULTATION_FEE")
                    .upiId(lawyer.getUpiId() != null ? lawyer.getUpiId() : "advocate@upi")
                    .category(category)
                    .build());
        }

        // 2. Include any consultation requests with status PAYMENT_COMPLETED, ACTIVE, or COMPLETED that didn't have a PaymentTransaction
        for (ConsultationRequest cr : consultationRequests) {
            if (!processedRequestIds.contains(cr.getId()) &&
                    (cr.getStatus() == ConsultationRequestStatus.PAYMENT_COMPLETED ||
                     cr.getStatus() == ConsultationRequestStatus.ACTIVE ||
                     cr.getStatus() == ConsultationRequestStatus.COMPLETED)) {

                BigDecimal amt = cr.getPaymentAmount() != null
                        ? cr.getPaymentAmount()
                        : (lawyer.getConsultationFee() != null
                            ? new BigDecimal(lawyer.getConsultationFee())
                            : new BigDecimal("199.00"));

                totalEarnings = totalEarnings.add(amt);
                if (cr.getCreatedAt() != null && cr.getCreatedAt().toLocalDate().isEqual(today)) {
                    todayEarnings = todayEarnings.add(amt);
                }

                String cName = cr.getCustomer() != null ? cr.getCustomer().getFullName() : "Direct Client";
                String cEmail = cr.getCustomer() != null ? cr.getCustomer().getEmail() : "";
                String cPhone = cr.getCustomer() != null ? cr.getCustomer().getMobileNumber() : "";
                String cat = cr.getCategory() != null ? cr.getCategory().name() : "LEGAL_CONSULTATION";
                String dateStr = cr.getCreatedAt() != null ? cr.getCreatedAt().format(formatter) : "Today";
                String rawDateStr = cr.getCreatedAt() != null ? cr.getCreatedAt().toString() : "";

                txDTOs.add(LawyerTransactionDTO.builder()
                        .id("ORD_CONS_" + cr.getId() + "_SYN")
                        .requestId(cr.getId())
                        .customerName(cName)
                        .customerEmail(cEmail)
                        .customerPhone(cPhone)
                        .amount("₹" + amt.setScale(2, RoundingMode.HALF_UP).toString())
                        .amountNum(amt)
                        .date(dateStr)
                        .rawDate(rawDateStr)
                        .duration("Consultation Session")
                        .status("PAID")
                        .paymentType("CONSULTATION_FEE")
                        .upiId(lawyer.getUpiId() != null ? lawyer.getUpiId() : "advocate@upi")
                        .category(cat)
                        .build());
            }
        }

        // Calculate completed / active consultations count
        long completedCount = consultationRequests.stream()
                .filter(cr -> cr.getStatus() == ConsultationRequestStatus.COMPLETED ||
                              cr.getStatus() == ConsultationRequestStatus.ACTIVE ||
                              cr.getStatus() == ConsultationRequestStatus.PAYMENT_COMPLETED)
                .count();

        return LawyerEarningsResponseDTO.builder()
                .totalEarnings("₹" + totalEarnings.setScale(2, RoundingMode.HALF_UP).toString())
                .totalEarningsNum(totalEarnings)
                .todayEarnings("₹" + todayEarnings.setScale(2, RoundingMode.HALF_UP).toString())
                .todayEarningsNum(todayEarnings)
                .completedConsultations((int) completedCount)
                .lawyerUpiId(lawyer.getUpiId() != null && !lawyer.getUpiId().isBlank() ? lawyer.getUpiId() : "advocate@upi")
                .lawyerName(lawyer.getFullName())
                .transactions(txDTOs)
                .build();
    }
}
