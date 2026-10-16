package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.CallEndDTO;
import com.adalat.dto.CallInitiateDTO;
import com.adalat.dto.CallResponseDTO;
import com.adalat.dto.ConsultationCallDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ConsultationCallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
@Tag(name = "Consultation Calls", description = "Endpoints for managing client-lawyer audio and video calling")
public class ConsultationCallController {

    private final ConsultationCallService consultationCallService;

    @GetMapping("/history/{consultationRequestId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Get call history", description = "Fetch all audio/video call records for a consultation")
    public ResponseEntity<ApiResponseDTO<List<ConsultationCallDTO>>> getCallHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long consultationRequestId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        List<ConsultationCallDTO> history = consultationCallService.getCallHistory(
                consultationRequestId,
                userDetails.getId(),
                userDetails.getRole().name()
        );
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Call history fetched successfully", history));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER')")
    @Operation(summary = "Get active call", description = "Check if the current authenticated user has an active call")
    public ResponseEntity<ApiResponseDTO<ConsultationCallDTO>> getActiveCall(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        ConsultationCallDTO call = consultationCallService.getActiveCall(userDetails.getId());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", call != null ? "Active call found" : "No active call", call));
    }

    @GetMapping("/{callId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER', 'ADMIN')")
    @Operation(summary = "Get call details", description = "Fetch details of a specific call")
    public ResponseEntity<ApiResponseDTO<ConsultationCallDTO>> getCallById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long callId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        ConsultationCallDTO call = consultationCallService.getCallById(
                callId,
                userDetails.getId(),
                userDetails.getRole().name()
        );
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Call details fetched successfully", call));
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER')")
    @Operation(summary = "Initiate call (REST fallback)", description = "Initiate a call via REST if socket unavailable")
    public ResponseEntity<ApiResponseDTO<ConsultationCallDTO>> initiateCall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CallInitiateDTO dto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        ConsultationCallDTO call = consultationCallService.initiateCall(
                dto.getConsultationRequestId(),
                userDetails.getId(),
                userDetails.getRole().name(),
                dto.getCallType()
        );
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Call initiated", call));
    }

    @PostMapping("/respond")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER')")
    @Operation(summary = "Respond to call (REST fallback)", description = "Accept or decline a call via REST")
    public ResponseEntity<ApiResponseDTO<ConsultationCallDTO>> respondToCall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CallResponseDTO dto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        ConsultationCallDTO call = consultationCallService.respondToCall(
                dto.getCallId(),
                userDetails.getId(),
                userDetails.getRole().name(),
                dto.getResponse()
        );
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Call response recorded", call));
    }

    @PostMapping("/end")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'LAWYER')")
    @Operation(summary = "End call (REST fallback)", description = "End active call via REST")
    public ResponseEntity<ApiResponseDTO<ConsultationCallDTO>> endCall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CallEndDTO dto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(new ApiResponseDTO<>("FAIL", "Authentication required", null));
        }
        ConsultationCallDTO call = consultationCallService.endCall(
                dto.getCallId(),
                userDetails.getId(),
                dto.getReason()
        );
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Call ended successfully", call));
    }
}
