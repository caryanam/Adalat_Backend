package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.service.LawyerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/lawyers")
@RequiredArgsConstructor
public class AdminLawyerController {

    private final LawyerService lawyerService;

    // GET all pending (SUBMITTED + PENDING) applications
    @GetMapping("/pending")
    public ResponseEntity<ApiResponseDTO<List<LawyerProfileResponseDTO>>> getPending() {
        List<LawyerProfileResponseDTO> list = lawyerService.getPendingLawyers();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Pending lawyer applications retrieved.", list));
    }

    // GET single lawyer detail
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> getById(
            @PathVariable Long id) {

        LawyerProfileResponseDTO response = lawyerService.getLawyerById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Lawyer retrieved.", response));
    }

    // APPROVE lawyer
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> approve(
            @PathVariable Long id) {

        LawyerProfileResponseDTO response = lawyerService.approveLawyer(id);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Lawyer approved. Account is now ACTIVE.", response));
    }

    // REJECT lawyer
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponseDTO<LawyerProfileResponseDTO>> reject(
            @PathVariable Long id,
            @Valid @RequestBody AdminRejectRequestDTO request) {

        LawyerProfileResponseDTO response = lawyerService.rejectLawyer(id, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Lawyer application rejected.", response));
    }
}
