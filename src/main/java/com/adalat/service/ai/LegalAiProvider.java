package com.adalat.service.ai;

import com.adalat.dto.ai.AiIntakeRequestDTO;
import com.adalat.dto.ai.AiIntakeResponseDTO;

public interface LegalAiProvider {
    /**
     * Sends the current conversation state to the external AI model
     * and returns the parsed structured response.
     *
     * @param request the current intake state and new user message
     * @return the structured response from the AI
     * @throws AiProviderException if the AI is unavailable, times out, or returns invalid JSON
     */
    AiIntakeResponseDTO processMessage(AiIntakeRequestDTO request);
}
