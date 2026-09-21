package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.enums.Language;
import com.adalat.enums.PracticeArea;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.LawyerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/lawyers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lawyer Profile", description = "Advocate profile management, OTP email verification, and password change")
public class LawyerProfileController {

    private final LawyerService lawyerService;

    private Long resolveId(Long lawyerId, CustomUserDetails userDetails) {
        if (lawyerId != null && lawyerId > 0) return lawyerId;
        if (userDetails != null && userDetails.getId() != null) return userDetails.getId();
        return 1L; // Fallback to initial seed advocate
    }

    // ─── GET Profile ───────────────────────────────────────────────────────────

    @GetMapping("/{lawyerId}/profile")
    @Operation(summary = "Get advocate profile by ID")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> getProfileById(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.getLawyerById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Profile fetched successfully.", response));
    }

    @GetMapping("/profile/me")
    @Operation(summary = "Get current advocate profile")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> getCurrentProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long id = resolveId(null, userDetails);
        LawyerProfileResponseDTO response = lawyerService.getLawyerById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Profile fetched successfully.", response));
    }

    // ─── UPDATE Profile ────────────────────────────────────────────────────────

    @PutMapping(value = {"/profile", "/{lawyerId}/profile"})
    @Operation(summary = "Update complete advocate profile")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updateProfile(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody LawyerUpdateProfileRequestDTO request) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.updateProfile(id, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Profile updated successfully.", response));
    }

    // ─── PROFILE PHOTO UPLOAD ──────────────────────────────────────────────────

    @PostMapping(value = {"/profile-photo", "/{lawyerId}/profile-photo"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload advocate profile photo")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> uploadProfilePhoto(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.updateProfilePhoto(id, file);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Profile photo uploaded successfully.", response));
    }

    // ─── EMAIL CHANGE & OTP FLOW ───────────────────────────────────────────────

    @PostMapping(value = {"/email/send-otp", "/{lawyerId}/email/send-otp"})
    @Operation(summary = "Send OTP to verify new email address")
    public ResponseEntity<ApiResponseDTO<Void>> sendEmailChangeOtp(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LawyerEmailChangeRequestDTO request) {
        Long id = resolveId(lawyerId, userDetails);
        lawyerService.sendEmailChangeOtp(id, request.getNewEmail());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "A 6-digit OTP has been sent to " + request.getNewEmail() + ". Please verify to complete email update.", null));
    }

    @PostMapping(value = {"/email/verify-update", "/{lawyerId}/email/verify-update"})
    @Operation(summary = "Verify OTP and update advocate email")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> verifyAndUpdateEmail(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LawyerEmailVerifyRequestDTO request) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.verifyAndUpdateEmail(
                id, request.getNewEmail(), request.getOtp());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Email address updated successfully.", response));
    }

    // ─── CHANGE PASSWORD & EMAIL NOTIFICATION ─────────────────────────────────

    @PutMapping(value = {"/change-password", "/{lawyerId}/change-password"})
    @Operation(summary = "Change advocate password and dispatch security email alert")
    public ResponseEntity<ApiResponseDTO<Void>> changePassword(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LawyerChangePasswordRequestDTO request) {
        Long id = resolveId(lawyerId, userDetails);
        lawyerService.changePassword(id, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Password changed successfully. A security notification has been sent to your email.", null));
    }

    @PostMapping("/forgot-password/reset")
    @Operation(summary = "Reset password after email OTP verification")
    public ResponseEntity<ApiResponseDTO<Void>> resetPasswordWithOtp(
            @Valid @RequestBody ForgotPasswordResetDTO request) {
        lawyerService.resetPasswordWithEmailOtp(request.getEmail(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Password reset successfully. A confirmation has been sent to your email.", null));
    }

    // ─── MODULAR SECTION UPDATES ──────────────────────────────────────────────

    @PutMapping(value = {"/pricing", "/{lawyerId}/pricing"})
    @Operation(summary = "Update consultation fee")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updatePricing(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        Long id = resolveId(lawyerId, userDetails);
        Integer fee = body.get("consultationFee") != null
                ? Integer.parseInt(body.get("consultationFee").toString())
                : (body.get("amount") != null ? Integer.parseInt(body.get("amount").toString()) : 99);
        LawyerProfileResponseDTO response = lawyerService.updatePricingFee(id, fee);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation pricing updated.", response));
    }

    @PutMapping(value = {"/categories", "/{lawyerId}/categories"})
    @Operation(summary = "Update practice areas / legal categories")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updateCategories(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Set<PracticeArea> practiceAreas) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.updatePracticeAreas(id, practiceAreas);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Practice categories updated.", response));
    }

    @PutMapping(value = {"/languages", "/{lawyerId}/languages"})
    @Operation(summary = "Update spoken languages")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updateLanguages(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Set<Language> languages) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerProfileResponseDTO response = lawyerService.updateLanguages(id, languages);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Languages updated.", response));
    }

    @PutMapping(value = {"/upi", "/{lawyerId}/upi"})
    @Operation(summary = "Update UPI ID")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updateUpi(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        Long id = resolveId(lawyerId, userDetails);
        String upiId = body.get("upiId");
        LawyerProfileResponseDTO response = lawyerService.updateUpiId(id, upiId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Payout UPI ID updated.", response));
    }

    @PutMapping(value = {"/overview", "/{lawyerId}/overview"})
    @Operation(summary = "Update professional overview")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> updateOverview(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        Long id = resolveId(lawyerId, userDetails);
        String bio = body.get("bio");
        LawyerProfileResponseDTO response = lawyerService.updateBio(id, bio);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Professional overview updated.", response));
    }

    // ─── ADVOCATE EARNINGS & PAYOUT HISTORY ───────────────────────────────────

    @GetMapping(value = {"/earnings", "/earnings/me", "/{lawyerId}/earnings"})
    @Operation(summary = "Get advocate consultation earnings & transaction payout history")
    public ResponseEntity<ApiResponseDTO<LawyerEarningsResponseDTO>> getEarnings(
            @PathVariable(required = false) Long lawyerId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long id = resolveId(lawyerId, userDetails);
        LawyerEarningsResponseDTO earnings = lawyerService.getLawyerEarnings(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Advocate earnings and payout history fetched successfully.", earnings));
    }
}
