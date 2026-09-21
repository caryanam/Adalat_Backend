package com.adalat.service;

import com.adalat.dto.*;
import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

public interface LawyerService {

    // Step 1 — Account
    LawyerProfileResponseDTO registerStep1(LawyerAccountRequestDTO request);

    // Step 2 — Professional Details
    LawyerProfileResponseDTO updateStep2(Long lawyerId, LawyerProfessionalRequestDTO request);

    // Step 4 — Pricing
    LawyerProfileResponseDTO updateStep4(Long lawyerId, LawyerPricingRequestDTO request);

    // Step 5 — UPI
    LawyerProfileResponseDTO updateStep5(Long lawyerId, LawyerUpiRequestDTO request);

    // Step 6 — Submit
    LawyerSubmitResponseDTO submitApplication(Long lawyerId);

    // Login
    LawyerLoginResponseDTO loginLawyer(LoginRequestDTO request);

    // Admin operations
    LawyerProfileResponseDTO approveLawyer(Long lawyerId);

    LawyerProfileResponseDTO rejectLawyer(Long lawyerId, AdminRejectRequestDTO request);

    List<LawyerProfileResponseDTO> getPendingLawyers();

    LawyerProfileResponseDTO getLawyerById(Long lawyerId);

    List<LawyerProfileResponseDTO> getPublicDirectoryLawyers();

    // ─── Profile & Security Operations ─────────────────────────────────────────
    LawyerProfileResponseDTO updateProfile(Long lawyerId, LawyerUpdateProfileRequestDTO request);

    LawyerProfileResponseDTO updateProfilePhoto(Long lawyerId, MultipartFile file);

    void sendEmailChangeOtp(Long lawyerId, String newEmail);

    LawyerProfileResponseDTO verifyAndUpdateEmail(Long lawyerId, String newEmail, String otp);

    void changePassword(Long lawyerId, LawyerChangePasswordRequestDTO request);

    void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword);

    LawyerProfileResponseDTO updatePricingFee(Long lawyerId, Integer consultationFee);

    LawyerProfileResponseDTO updatePracticeAreas(Long lawyerId, Set<PracticeArea> practiceAreas);

    LawyerProfileResponseDTO updateLanguages(Long lawyerId, Set<Language> languages);

    LawyerProfileResponseDTO updateUpiId(Long lawyerId, String upiId);

    LawyerProfileResponseDTO updateBio(Long lawyerId, String bio);

    LawyerEarningsResponseDTO getLawyerEarnings(Long lawyerId);
}
