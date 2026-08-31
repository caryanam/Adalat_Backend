package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.LegalAssistanceSessionRepository;
import com.adalat.service.ConsultationRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationRequestServiceImpl implements ConsultationRequestService {

    private final ConsultationRequestRepository consultationRequestRepository;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;
    private final LegalAssistanceSessionRepository sessionRepository;

    @Override
    @Transactional
    public ConsultationRequestResponseDTO createRequest(Long customerId, Long sessionId, Long lawyerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        LegalAssistanceSession session = sessionRepository.findByIdAndCustomer(sessionId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Legal assistance session not found or does not belong to you: " + sessionId));

        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        // Check if an existing request is pending or active
        ConsultationRequest request = consultationRequestRepository.findByLegalSessionAndLawyer(session, lawyer)
                .orElseGet(() -> ConsultationRequest.builder()
                        .customer(customer)
                        .legalSession(session)
                        .lawyer(lawyer)
                        .category(session.getAiDetectedCategory() != null ? session.getAiDetectedCategory() : session.getSelectedCategory())
                        .practiceArea(session.getPracticeArea())
                        .caseSummary(session.getSummary())
                        .status(ConsultationRequestStatus.REQUESTED)
                        .build());

        request.setStatus(ConsultationRequestStatus.REQUESTED);
        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request created: requestId={}, customerId={}, lawyerId={}, sessionId={}",
                saved.getId(), customerId, lawyerId, sessionId);

        return toDTO(saved);
    }

    @Override
    public List<ConsultationRequestResponseDTO> getRequestsForLawyer(Long lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        return consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO acceptRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (!request.getLawyer().getLawyerId().equals(lawyerId)) {
            throw new IllegalArgumentException("You are not authorized to accept this consultation request.");
        }

        request.setStatus(ConsultationRequestStatus.ACCEPTED);
        if (actionDTO != null && actionDTO.getNotes() != null) {
            request.setLawyerNotes(actionDTO.getNotes());
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request accepted: requestId={}, lawyerId={}", requestId, lawyerId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO rejectRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (!request.getLawyer().getLawyerId().equals(lawyerId)) {
            throw new IllegalArgumentException("You are not authorized to reject this consultation request.");
        }

        request.setStatus(ConsultationRequestStatus.REJECTED);
        if (actionDTO != null && actionDTO.getNotes() != null) {
            request.setLawyerNotes(actionDTO.getNotes());
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request rejected: requestId={}, lawyerId={}", requestId, lawyerId);
        return toDTO(saved);
    }

    @Override
    public List<ConsultationRequestResponseDTO> getRequestsForCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        return consultationRequestRepository.findByCustomerOrderByCreatedAtDesc(customer)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private ConsultationRequestResponseDTO toDTO(ConsultationRequest req) {
        String instruction = switch (req.getStatus()) {
            case REQUESTED -> "Consultation request sent to advocate. Awaiting advocate's acceptance.";
            case ACCEPTED -> "Lawyer is open to consult with you. Please complete the consultation fee payment to start your session.";
            case REJECTED -> "Lawyer is currently unavailable for this matter. Please select another recommended advocate.";
            case PAYMENT_PENDING -> "Please proceed to complete the consultation payment.";
            case PAYMENT_COMPLETED, ACTIVE -> "Consultation is active. You can now communicate directly with the lawyer.";
            case COMPLETED -> "Consultation has concluded successfully.";
            case CANCELLED -> "Consultation request was cancelled.";
            default -> "Consultation status updated.";
        };

        return ConsultationRequestResponseDTO.builder()
                .id(req.getId())
                .sessionId(req.getLegalSession() != null ? req.getLegalSession().getId() : null)
                .customerId(req.getCustomer().getCustomerId())
                .customerName(req.getCustomer().getFullName())
                .customerEmail(req.getCustomer().getEmail())
                .customerMobileNumber(req.getCustomer().getMobileNumber())
                .lawyerId(req.getLawyer().getLawyerId())
                .lawyerName(req.getLawyer().getFullName())
                .lawyerLocation(req.getLawyer().getLocation())
                .lawyerRate(req.getLawyer().getConsultationRate() != null ? req.getLawyer().getConsultationRate().getAmount() : 99)
                .category(req.getCategory())
                .categoryDisplayName(req.getCategory() != null ? req.getCategory().getDisplayName() : null)
                .practiceArea(req.getPracticeArea())
                .caseSummary(req.getCaseSummary())
                .status(req.getStatus())
                .lawyerNotes(req.getLawyerNotes())
                .nextStepInstruction(instruction)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }
}
