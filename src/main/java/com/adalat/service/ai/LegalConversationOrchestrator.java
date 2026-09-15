package com.adalat.service.ai;

import com.adalat.dto.ai.CustomerMessageRequestDTO;
import com.adalat.dto.ai.LegalIntakeResponseDTO;

public interface LegalConversationOrchestrator {

    /**
     * Processes an incoming message from the customer, manages the intake lifecycle,
     * calls the AI provider, performs validation and anti-looping checks, and persists results.
     */
    LegalIntakeResponseDTO processCustomerMessage(Long customerId, Long sessionId, CustomerMessageRequestDTO request);

    /**
     * Retrieves the current state of an intake session for an authenticated customer.
     */
    LegalIntakeResponseDTO getSessionState(Long customerId, Long sessionId);

    /**
     * Gets or creates an active intake session for the customer.
     * @param forceNew If true, abandons any existing active session and starts a new one.
     */
    LegalIntakeResponseDTO getOrCreateActiveSession(Long customerId, boolean forceNew);

    /**
     * Confirms the case summary and transitions the session to AWAITING_NEXT_STEP.
     * Customer can then choose CONNECT_LAWYER or AI_ONLY.
     */
    LegalIntakeResponseDTO confirmSummary(Long customerId, Long sessionId);

    /**
     * Handles the customer's next-step choice after summary confirmation.
     * @param action Must be "CONNECT_LAWYER" or "AI_ONLY"
     */
    LegalIntakeResponseDTO handleNextStepChoice(Long customerId, Long sessionId, String action);

    /**
     * Retrieves all sessions for a specific customer, ordered by newest first.
     */
    java.util.List<com.adalat.dto.ai.SessionSummaryDTO> getAllSessions(Long customerId);
}

