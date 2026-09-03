package com.adalat.serviceImpl;

import com.adalat.dto.*;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.enums.*;
import com.adalat.exception.DuplicateResourceException;
import com.adalat.exception.LawyerNotApprovedException;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.LawyerDocumentRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.security.CustomUserDetails;
import com.adalat.security.JwtService;
import com.adalat.service.LawyerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawyerServiceImpl implements LawyerService {

    private final LawyerRepository lawyerRepository;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
                .build();

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

    // ─── HELPERS ───────────────────────────────────────────────────────────────

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
}

