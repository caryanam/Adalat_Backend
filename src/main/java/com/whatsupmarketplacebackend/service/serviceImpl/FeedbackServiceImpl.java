package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.FeedbackRequestDTO;
import com.whatsupmarketplacebackend.dto.FeedbackResponseDTO;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.entity.Feedback;
import com.whatsupmarketplacebackend.enums.FeedbackStatus;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.repository.FeedbackRepository;
import com.whatsupmarketplacebackend.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ClientRepository clientRepository;

    //For Client
    @Override
    @Transactional
    public FeedbackResponseDTO createFeedback(Long clientId, FeedbackRequestDTO requestDTO) {
        log.info("Creating new feedback for clientId: {}", clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + clientId));

        Feedback feedback = Feedback.builder()
                .client(client)
                .clientName(requestDTO.clientName() != null && !requestDTO.clientName().isBlank()
                        ? requestDTO.clientName() : client.getOwnerName())
                .companyName(requestDTO.companyName() != null && !requestDTO.companyName().isBlank()
                        ? requestDTO.companyName() : client.getCompanyName())
                .designation(requestDTO.designation())
                .serviceName(requestDTO.serviceName())
                .rating(requestDTO.rating())
                .comment(requestDTO.comment())
                .status(FeedbackStatus.PENDING)
                .build();

        Feedback savedFeedback = feedbackRepository.save(feedback);
        return mapToResponseDTO(savedFeedback);
    }

    //For Client
    @Override
    @Transactional
    public FeedbackResponseDTO updateFeedback(Long id, FeedbackRequestDTO requestDTO) {
        log.info("Updating feedback with id: {}", id);
        Feedback existingFeedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));

        if (requestDTO.clientId() != null) {
            Client client = clientRepository.findById(requestDTO.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + requestDTO.clientId()));
            existingFeedback.setClient(client);
        }

        existingFeedback.setClientName(requestDTO.clientName());
        existingFeedback.setCompanyName(requestDTO.companyName());
        existingFeedback.setDesignation(requestDTO.designation());
        existingFeedback.setServiceName(requestDTO.serviceName());
        existingFeedback.setRating(requestDTO.rating());
        existingFeedback.setComment(requestDTO.comment());

        Feedback updatedFeedback = feedbackRepository.save(existingFeedback);
        return mapToResponseDTO(updatedFeedback);
    }

    //For Admin
    @Override
    @Transactional
    public FeedbackResponseDTO updateStatus(Long id, FeedbackStatus status) {
        log.info("Updating status for feedback id: {} to {}", id, status);
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));
        feedback.setStatus(status);
        Feedback updatedFeedback = feedbackRepository.save(feedback);
        return mapToResponseDTO(updatedFeedback);
    }

    //For Client
    @Override
    @Transactional
    public void deleteFeedback(Long id) {
        log.info("Deleting feedback with id: {}", id);
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));
        feedbackRepository.delete(feedback);
    }

    //For Admin
    @Override
    @Transactional(readOnly = true)
    public FeedbackResponseDTO getFeedbackById(Long id) {
        log.info("Fetching feedback by id: {}", id);
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));
        return mapToResponseDTO(feedback);
    }

    //For Admin
    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getAllFeedback() {
        log.info("Fetching all feedback");
        return feedbackRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private FeedbackResponseDTO mapToResponseDTO(Feedback feedback) {
        return FeedbackResponseDTO.builder()
                .id(feedback.getId())
                .clientId(feedback.getClient() != null ? feedback.getClient().getId() : null)
                .clientName(feedback.getClientName())
                .companyName(feedback.getCompanyName())
                .designation(feedback.getDesignation())
                .serviceName(feedback.getServiceName())
                .rating(feedback.getRating())
                .comment(feedback.getComment())
                .status(feedback.getStatus())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
