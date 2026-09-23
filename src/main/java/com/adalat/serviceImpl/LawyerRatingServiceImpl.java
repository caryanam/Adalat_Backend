package com.adalat.serviceImpl;

import com.adalat.dto.LawyerRatingSummaryDTO;
import com.adalat.dto.LawyerReviewRequestDTO;
import com.adalat.dto.LawyerReviewResponseDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerReview;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.LawyerReviewRepository;
import com.adalat.service.LawyerRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawyerRatingServiceImpl implements LawyerRatingService {

    private final LawyerRepository lawyerRepository;
    private final LawyerReviewRepository lawyerReviewRepository;
    private final CustomerRepository customerRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final com.adalat.service.NotificationService notificationService;

    @Override
    @Transactional
    public LawyerReviewResponseDTO submitRating(Long lawyerId, Long customerId, LawyerReviewRequestDTO requestDTO) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        Customer customer = null;
        String customerName = requestDTO.getCustomerName();
        if (customerId != null) {
            customer = customerRepository.findById(customerId).orElse(null);
            if (customer != null && (customerName == null || customerName.trim().isEmpty())) {
                customerName = customer.getFullName();
            }
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = "Verified Client";
        }

        LawyerReview review = LawyerReview.builder()
                .lawyer(lawyer)
                .customer(customer)
                .customerName(customerName)
                .consultationRequestId(requestDTO.getConsultationRequestId())
                .rating(requestDTO.getRating())
                .comment(requestDTO.getComment())
                .build();

        review = lawyerReviewRepository.save(review);

        // Recalculate average rating & rating count for lawyer
        updateLawyerAggregateRating(lawyer);

        // Dispatch notification
        try {
            notificationService.createNotification(
                    com.adalat.enums.Role.LAWYER,
                    lawyer.getLawyerId(),
                    "New Client Review Received",
                    "Client " + customerName + " rated you " + review.getRating() + "★" + (review.getComment() != null && !review.getComment().isBlank() ? ": \"" + review.getComment() + "\"" : "."),
                    com.adalat.enums.NotificationType.NEW_REVIEW_RECEIVED,
                    review.getId(),
                    "REVIEW",
                    "/lawyer/dashboard"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch submitRating notification: {}", notifEx.getMessage());
        }

        return mapToDTO(review);
    }

    @Override
    @Transactional
    public LawyerReviewResponseDTO submitConsultationRating(Long customerId, Long consultationId, LawyerReviewRequestDTO requestDTO) {
        ConsultationRequest consultation = consultationRequestRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found with ID: " + consultationId));

        Lawyer lawyer = consultation.getLawyer();
        if (lawyer == null) {
            throw new ResourceNotFoundException("No advocate assigned to consultation: " + consultationId);
        }

        Customer customer = consultation.getCustomer();
        if (customer == null && customerId != null) {
            customer = customerRepository.findById(customerId).orElse(null);
        }

        String customerName = requestDTO.getCustomerName();
        if (customer != null && (customerName == null || customerName.trim().isEmpty())) {
            customerName = customer.getFullName();
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = "Verified Client";
        }

        // Check if a review already exists for this consultation request
        Optional<LawyerReview> existing = lawyerReviewRepository.findByConsultationRequestId(consultationId);
        LawyerReview review;
        if (existing.isPresent()) {
            review = existing.get();
            review.setRating(requestDTO.getRating());
            review.setComment(requestDTO.getComment());
            review.setCustomerName(customerName);
        } else {
            review = LawyerReview.builder()
                    .lawyer(lawyer)
                    .customer(customer)
                    .customerName(customerName)
                    .consultationRequestId(consultationId)
                    .rating(requestDTO.getRating())
                    .comment(requestDTO.getComment())
                    .build();
        }

        review = lawyerReviewRepository.save(review);

        // Also update consultation request entity
        consultation.setRating(requestDTO.getRating());
        consultation.setRatingComment(requestDTO.getComment());
        consultation.setRatedAt(LocalDateTime.now());
        consultationRequestRepository.save(consultation);

        // Recalculate average rating & rating count in lawyerReg
        updateLawyerAggregateRating(lawyer);

        // Dispatch notification to lawyer
        try {
            notificationService.createNotification(
                    com.adalat.enums.Role.LAWYER,
                    lawyer.getLawyerId(),
                    "New Consultation Review",
                    "Client " + customerName + " rated your consultation " + review.getRating() + "★" + (review.getComment() != null && !review.getComment().isBlank() ? ": \"" + review.getComment() + "\"" : "."),
                    com.adalat.enums.NotificationType.NEW_REVIEW_RECEIVED,
                    review.getId(),
                    "REVIEW",
                    "/lawyer/dashboard"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch submitConsultationRating notification: {}", notifEx.getMessage());
        }

        return mapToDTO(review);
    }

    @Override
    @Transactional(readOnly = true)
    public LawyerRatingSummaryDTO getLawyerRatings(Long lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        List<LawyerReview> reviews = lawyerReviewRepository.findByLawyer_LawyerIdOrderByCreatedAtDesc(lawyerId);
        List<LawyerReviewResponseDTO> reviewDTOs = reviews.stream().map(this::mapToDTO).toList();

        double avg = reviews.isEmpty() ? 0.0 : reviews.stream().mapToInt(LawyerReview::getRating).average().orElse(0.0);
        double roundedAvg = Math.round(avg * 10.0) / 10.0;

        return LawyerRatingSummaryDTO.builder()
                .lawyerId(lawyer.getLawyerId())
                .averageRating(lawyer.getRating() != null && lawyer.getRating() > 0 ? lawyer.getRating() : roundedAvg)
                .ratingCount(lawyer.getRatingCount() != null ? lawyer.getRatingCount() : reviews.size())
                .reviews(reviewDTOs)
                .build();
    }

    private void updateLawyerAggregateRating(Lawyer lawyer) {
        List<LawyerReview> reviews = lawyerReviewRepository.findByLawyer_LawyerIdOrderByCreatedAtDesc(lawyer.getLawyerId());
        if (reviews.isEmpty()) {
            lawyer.setRating(0.0);
            lawyer.setRatingCount(0);
        } else {
            double avg = reviews.stream().mapToInt(LawyerReview::getRating).average().orElse(0.0);
            double roundedAvg = Math.round(avg * 10.0) / 10.0;
            lawyer.setRating(roundedAvg);
            lawyer.setRatingCount(reviews.size());
        }
        lawyerRepository.save(lawyer);
        log.info("Updated Lawyer (ID: {}) aggregate rating to {} (count: {})", lawyer.getLawyerId(), lawyer.getRating(), lawyer.getRatingCount());
    }

    private LawyerReviewResponseDTO mapToDTO(LawyerReview review) {
        return LawyerReviewResponseDTO.builder()
                .id(review.getId())
                .lawyerId(review.getLawyer() != null ? review.getLawyer().getLawyerId() : null)
                .customerId(review.getCustomer() != null ? review.getCustomer().getCustomerId() : null)
                .customerName(review.getCustomerName())
                .consultationRequestId(review.getConsultationRequestId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt() != null ? review.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
