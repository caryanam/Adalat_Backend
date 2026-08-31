package com.adalat.service;

import com.adalat.dto.*;

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

    java.util.List<LawyerProfileResponseDTO> getPendingLawyers();

    LawyerProfileResponseDTO getLawyerById(Long lawyerId);
}
