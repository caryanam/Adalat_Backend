package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.FeedbackRequestDTO;
import com.whatsupmarketplacebackend.dto.FeedbackResponseDTO;
import com.whatsupmarketplacebackend.enums.FeedbackStatus;

import java.util.List;

public interface FeedbackService {

    FeedbackResponseDTO createFeedback(Long clientId, FeedbackRequestDTO requestDTO);

    FeedbackResponseDTO updateFeedback(Long id, FeedbackRequestDTO requestDTO);

    FeedbackResponseDTO updateStatus(Long id, FeedbackStatus status);

    void deleteFeedback(Long id);

    FeedbackResponseDTO getFeedbackById(Long id);

    List<FeedbackResponseDTO> getAllFeedback();
}
