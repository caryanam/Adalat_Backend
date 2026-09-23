package com.adalat.controller;

import com.adalat.dto.DeleteAccountRequestDTO;
import com.adalat.dto.DeleteAccountResponseDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account Management", description = "Endpoints for managing user accounts, security, and permanent account deletion")
public class AccountController {

    private final AccountService accountService;

    @DeleteMapping("/delete")
    @Operation(summary = "Permanently Delete Account", description = "Securely deletes the authenticated user's account and all associated entities.")
    public ResponseEntity<DeleteAccountResponseDTO> deleteAccountViaDelete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteAccountRequestDTO request) {

        DeleteAccountResponseDTO response = accountService.deleteAccount(userDetails, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete")
    @Operation(summary = "Permanently Delete Account (POST fallback)", description = "Alternative POST endpoint for HTTP clients that do not send bodies in DELETE requests.")
    public ResponseEntity<DeleteAccountResponseDTO> deleteAccountViaPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeleteAccountRequestDTO request) {

        DeleteAccountResponseDTO response = accountService.deleteAccount(userDetails, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
