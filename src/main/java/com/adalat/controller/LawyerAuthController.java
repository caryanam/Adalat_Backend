package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.LawyerLoginResponseDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.service.LawyerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lawyers")
@RequiredArgsConstructor
public class LawyerAuthController {

    private final LawyerService lawyerService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<LawyerLoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {

        LawyerLoginResponseDTO response = lawyerService.loginLawyer(request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Login successful", response));
    }
}
