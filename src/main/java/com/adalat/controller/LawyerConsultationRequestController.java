package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ConsultationRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lawyer/consultation-requests")
@RequiredArgsConstructor
public class LawyerConsultationRequestController {

    private final ConsultationRequestService consultationRequestService;

    private Long getLawyerId(CustomUserDetails userDetails) {
        return userDetails != null ? userDetails.getId() : 1L;
    }

    // ─── 1. GET ALL REQUESTS FOR AUTHENTICATED LAWYER ──────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ConsultationRequestResponseDTO>>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<ConsultationRequestResponseDTO> requests = consultationRequestService.getRequestsForLawyer(getLawyerId(userDetails));
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation requests retrieved.", requests));
    }

    // ─── 2. ACCEPT CONSULTATION REQUEST ────────────────────────────────────────
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> acceptRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) LawyerConsultationActionRequestDTO actionDTO) {

        ConsultationRequestResponseDTO response = consultationRequestService.acceptRequest(getLawyerId(userDetails), requestId, actionDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation request accepted.", response));
    }

    // ─── 3. REJECT CONSULTATION REQUEST ────────────────────────────────────────
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponseDTO<ConsultationRequestResponseDTO>> rejectRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody(required = false) LawyerConsultationActionRequestDTO actionDTO) {

        ConsultationRequestResponseDTO response = consultationRequestService.rejectRequest(getLawyerId(userDetails), requestId, actionDTO);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Consultation request rejected.", response));
    }
}
