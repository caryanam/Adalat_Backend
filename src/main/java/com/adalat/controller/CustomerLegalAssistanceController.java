package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.dto.ai.CustomerMessageRequestDTO;
import com.adalat.dto.ai.LegalIntakeResponseDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.ai.LegalConversationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.adalat.dto.ai.NextStepRequestDTO;

@RestController
@RequestMapping("/api/customer/legal-assistance")
@RequiredArgsConstructor
@Tag(name = "Legal AI Assistant", description = "Endpoints for conversational legal intake and case organization")
public class CustomerLegalAssistanceController {

    private final LegalConversationOrchestrator orchestrator;

    @PostMapping("/sessions")
    @Operation(summary = "Get or create active intake session", description = "Resumes an existing active session or initializes a new one for the authenticated customer. Use forceNew=true to abandon current and start over.")
    public ResponseEntity<ApiResponseDTO<LegalIntakeResponseDTO>> getOrCreateSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "false") boolean forceNew) {

        Long customerId = resolveCustomerId(userDetails);
        LegalIntakeResponseDTO response = orchestrator.getOrCreateActiveSession(customerId, forceNew);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Active session retrieved.", response));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get intake session state", description = "Fetches the current conversation state and summaries for the session")
    public ResponseEntity<ApiResponseDTO<LegalIntakeResponseDTO>> getSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        Long customerId = resolveCustomerId(userDetails);
        LegalIntakeResponseDTO response = orchestrator.getSessionState(customerId, sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Session state retrieved.", response));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Send message to AI Legal Assistant", description = "Processes a customer message through the legal intake engine")
    public ResponseEntity<ApiResponseDTO<LegalIntakeResponseDTO>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody CustomerMessageRequestDTO request) {

        Long customerId = resolveCustomerId(userDetails);
        LegalIntakeResponseDTO response = orchestrator.processCustomerMessage(customerId, sessionId, request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Message processed successfully.", response));
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    @Operation(summary = "Confirm intake case summary", description = "Confirms the summary and transitions session to AWAITING_NEXT_STEP.")
    public ResponseEntity<ApiResponseDTO<LegalIntakeResponseDTO>> confirmSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {

        Long customerId = resolveCustomerId(userDetails);
        LegalIntakeResponseDTO response = orchestrator.confirmSummary(customerId, sessionId);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Summary confirmed.", response));
    }

    @PostMapping("/sessions/{sessionId}/next-step")
    @Operation(summary = "Choose next step after intake", description = "Processes next action: CONNECT_LAWYER or AI_ONLY")
    public ResponseEntity<ApiResponseDTO<LegalIntakeResponseDTO>> nextStep(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody NextStepRequestDTO request) {

        Long customerId = resolveCustomerId(userDetails);
        LegalIntakeResponseDTO response = orchestrator.handleNextStepChoice(customerId, sessionId, request.getAction());
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Next step processed.", response));
    }

    private Long resolveCustomerId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getId() == null) {
            throw new SecurityException("User must be authenticated to access legal assistance.");
        }
        return userDetails.getId();
    }
}
