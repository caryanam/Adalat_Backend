package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationPaymentInitiateDTO;
import com.adalat.dto.ConsultationPaymentVerifyDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.PaymentStatus;
import com.adalat.exception.ResourceConflictException;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.LegalAssistanceSessionRepository;
import com.adalat.repository.PaymentTransactionRepository;
import com.adalat.service.ConsultationRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationRequestServiceImpl implements ConsultationRequestService {

    private final ConsultationRequestRepository consultationRequestRepository;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;
    private final LegalAssistanceSessionRepository sessionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional
    public ConsultationRequestResponseDTO createRequest(Long customerId, Long sessionId, Long lawyerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        LegalAssistanceSession session = sessionRepository.findByIdAndCustomer(sessionId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Legal assistance session not found or does not belong to you: " + sessionId));

        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        BigDecimal fee = lawyer.getConsultationRate() != null
                ? new BigDecimal(lawyer.getConsultationRate().getAmount())
                : new BigDecimal("99.00");

        // Check if an existing request exists
        Optional<ConsultationRequest> existingOpt = consultationRequestRepository.findByLegalSessionAndLawyer(session, lawyer);
        ConsultationRequest request;

        if (existingOpt.isPresent()) {
            ConsultationRequest existing = existingOpt.get();
            ConsultationRequestStatus currentStatus = existing.getStatus();

            if (currentStatus == ConsultationRequestStatus.REQUESTED) {
                throw new ResourceConflictException("You have already sent a consultation request to this advocate. Waiting for approval.");
            } else if (currentStatus == ConsultationRequestStatus.ACCEPTED || currentStatus == ConsultationRequestStatus.PAYMENT_PENDING) {
                throw new ResourceConflictException("Your consultation request has already been accepted by this advocate. Please proceed with payment.");
            } else if (currentStatus == ConsultationRequestStatus.PAYMENT_COMPLETED || currentStatus == ConsultationRequestStatus.ACTIVE) {
                throw new ResourceConflictException("You already have an active consultation session with this advocate.");
            } else if (currentStatus == ConsultationRequestStatus.COMPLETED) {
                throw new ResourceConflictException("The consultation with this advocate has already concluded for this legal matter.");
            }

            // Only allow re-request if previously REJECTED or CANCELLED
            existing.setStatus(ConsultationRequestStatus.REQUESTED);
            existing.setLawyerNotes(null);
            existing.setPaymentAmount(fee);
            request = existing;
        } else {
            request = ConsultationRequest.builder()
                    .customer(customer)
                    .legalSession(session)
                    .lawyer(lawyer)
                    .category(session.getAiDetectedCategory() != null ? session.getAiDetectedCategory() : session.getSelectedCategory())
                    .practiceArea(session.getPracticeArea())
                    .caseSummary(session.getSummary())
                    .paymentAmount(fee)
                    .status(ConsultationRequestStatus.REQUESTED)
                    .build();
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request created/updated: requestId={}, customerId={}, lawyerId={}, sessionId={}",
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

        if (request.getStatus() != ConsultationRequestStatus.REQUESTED) {
            throw new IllegalArgumentException("Cannot accept request in status: " + request.getStatus());
        }

        request.setStatus(ConsultationRequestStatus.ACCEPTED);
        if (actionDTO != null && actionDTO.getNotes() != null) {
            request.setLawyerNotes(actionDTO.getNotes());
        }

        if (request.getPaymentAmount() == null) {
            BigDecimal fee = request.getLawyer().getConsultationRate() != null
                    ? new BigDecimal(request.getLawyer().getConsultationRate().getAmount())
                    : new BigDecimal("99.00");
            request.setPaymentAmount(fee);
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

        if (request.getStatus() != ConsultationRequestStatus.REQUESTED) {
            throw new IllegalArgumentException("Cannot reject request in status: " + request.getStatus());
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

    @Override
    public ConsultationRequestResponseDTO getConsultationForCustomer(Long customerId, Long requestId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found for customer: " + requestId));

        return toDTO(request);
    }

    @Override
    public ConsultationRequestResponseDTO getConsultationForLawyer(Long lawyerId, Long requestId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndLawyer(requestId, lawyer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found for lawyer: " + requestId));

        return toDTO(request);
    }

    @Override
    public List<ConsultationRequestResponseDTO> getCustomerConsultations(Long customerId, String statusFilter) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        List<ConsultationRequest> list;
        if (statusFilter != null && !statusFilter.isBlank()) {
            String filter = statusFilter.toLowerCase().trim();
            if (filter.equals("active") || filter.equals("upcoming")) {
                list = consultationRequestRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(
                        customer, List.of(
                                ConsultationRequestStatus.REQUESTED,
                                ConsultationRequestStatus.ACCEPTED,
                                ConsultationRequestStatus.PAYMENT_PENDING,
                                ConsultationRequestStatus.PAYMENT_COMPLETED,
                                ConsultationRequestStatus.ACTIVE
                        )
                );
            } else if (filter.equals("completed")) {
                list = consultationRequestRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(
                        customer, List.of(ConsultationRequestStatus.COMPLETED)
                );
            } else if (filter.equals("cancelled") || filter.equals("rejected")) {
                list = consultationRequestRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(
                        customer, List.of(ConsultationRequestStatus.REJECTED, ConsultationRequestStatus.CANCELLED)
                );
            } else {
                list = consultationRequestRepository.findByCustomerOrderByCreatedAtDesc(customer);
            }
        } else {
            list = consultationRequestRepository.findByCustomerOrderByCreatedAtDesc(customer);
        }

        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ConsultationRequestResponseDTO> getLawyerConsultations(Long lawyerId, String statusFilter) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        List<ConsultationRequest> list;
        if (statusFilter != null && !statusFilter.isBlank()) {
            String filter = statusFilter.toLowerCase().trim();
            if (filter.equals("active") || filter.equals("upcoming")) {
                list = consultationRequestRepository.findByLawyerAndStatusInOrderByCreatedAtDesc(
                        lawyer, List.of(
                                ConsultationRequestStatus.ACCEPTED,
                                ConsultationRequestStatus.PAYMENT_PENDING,
                                ConsultationRequestStatus.PAYMENT_COMPLETED,
                                ConsultationRequestStatus.ACTIVE
                        )
                );
            } else if (filter.equals("requested") || filter.equals("pending")) {
                list = consultationRequestRepository.findByLawyerAndStatusInOrderByCreatedAtDesc(
                        lawyer, List.of(ConsultationRequestStatus.REQUESTED)
                );
            } else if (filter.equals("completed")) {
                list = consultationRequestRepository.findByLawyerAndStatusInOrderByCreatedAtDesc(
                        lawyer, List.of(ConsultationRequestStatus.COMPLETED)
                );
            } else {
                list = consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer);
            }
        } else {
            list = consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer);
        }

        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationPaymentInitiateDTO initiateConsultationPayment(Long customerId, Long requestId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found or does not belong to you: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACCEPTED && request.getStatus() != ConsultationRequestStatus.PAYMENT_PENDING) {
            throw new IllegalArgumentException("Consultation payment can only be initiated for ACCEPTED requests. Current status: " + request.getStatus());
        }

        BigDecimal amount = request.getPaymentAmount();
        if (amount == null) {
            amount = request.getLawyer().getConsultationRate() != null
                    ? new BigDecimal(request.getLawyer().getConsultationRate().getAmount())
                    : new BigDecimal("99.00");
            request.setPaymentAmount(amount);
        }

        request.setStatus(ConsultationRequestStatus.PAYMENT_PENDING);
        consultationRequestRepository.save(request);

        String orderId = "NYS-CONS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .customer(customer)
                .consultationRequest(request)
                .orderId(orderId)
                .amount(amount)
                .paymentType("CONSULTATION")
                .status(PaymentStatus.PENDING)
                .build();

        paymentTransactionRepository.save(transaction);
        log.info("Consultation payment initiated: orderId={}, requestId={}, customerId={}, amount={}",
                orderId, requestId, customerId, amount);

        return ConsultationPaymentInitiateDTO.builder()
                .orderId(orderId)
                .amount(amount)
                .consultationRequestId(request.getId())
                .customerId(customer.getCustomerId())
                .lawyerId(request.getLawyer().getLawyerId())
                .lawyerName(request.getLawyer().getFullName())
                .currency("INR")
                .message("Consultation fee payment order created. Complete payment to start your consultation.")
                .build();
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO verifyConsultationPayment(Long customerId, Long requestId, ConsultationPaymentVerifyDTO verifyDTO) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(verifyDTO.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found: " + verifyDTO.getOrderId()));

        if (!transaction.getCustomer().getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Payment order does not belong to this customer.");
        }

        if (transaction.getConsultationRequest() == null || !transaction.getConsultationRequest().getId().equals(requestId)) {
            throw new IllegalArgumentException("Payment order does not match this consultation request.");
        }

        if (verifyDTO.getGatewayPaymentId() != null) {
            transaction.setGatewayPaymentId(verifyDTO.getGatewayPaymentId());
        }
        if (verifyDTO.getGatewaySignature() != null) {
            transaction.setGatewaySignature(verifyDTO.getGatewaySignature());
        }

        transaction.setStatus(PaymentStatus.PAID);
        paymentTransactionRepository.save(transaction);

        // Transition consultation to ACTIVE
        request.setStatus(ConsultationRequestStatus.ACTIVE);
        ConsultationRequest saved = consultationRequestRepository.save(request);

        log.info("Consultation payment verified: orderId={}, requestId={}, status=ACTIVE",
                verifyDTO.getOrderId(), requestId);

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO completeConsultationByCustomer(Long customerId, Long requestId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE consultations can be marked as COMPLETED. Current status: " + request.getStatus());
        }

        request.setStatus(ConsultationRequestStatus.COMPLETED);
        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation completed by customer: requestId={}", requestId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO completeConsultationByLawyer(Long lawyerId, Long requestId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer not found with ID: " + lawyerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndLawyer(requestId, lawyer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE consultations can be marked as COMPLETED. Current status: " + request.getStatus());
        }

        request.setStatus(ConsultationRequestStatus.COMPLETED);
        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation completed by lawyer: requestId={}", requestId);
        return toDTO(saved);
    }

    private ConsultationRequestResponseDTO toDTO(ConsultationRequest req) {
        String instruction = switch (req.getStatus()) {
            case REQUESTED -> "Consultation request sent to advocate. Awaiting advocate's acceptance.";
            case ACCEPTED -> "Lawyer accepted your request! Please complete the consultation fee payment of ₹" + (req.getPaymentAmount() != null ? req.getPaymentAmount() : 99) + " to activate your chat session.";
            case REJECTED -> "Lawyer is currently unavailable for this matter. Please select another recommended advocate.";
            case PAYMENT_PENDING -> "Payment is pending. Complete payment to start your direct consultation session.";
            case PAYMENT_COMPLETED, ACTIVE -> "Consultation is active. You can now chat directly with the advocate in real-time.";
            case COMPLETED -> "Consultation has concluded successfully.";
            case CANCELLED -> "Consultation was cancelled.";
            default -> "Consultation status updated.";
        };

        PaymentStatus paymentStatus = null;
        if (req.getStatus() == ConsultationRequestStatus.ACTIVE || req.getStatus() == ConsultationRequestStatus.COMPLETED) {
            paymentStatus = PaymentStatus.PAID;
        } else if (req.getStatus() == ConsultationRequestStatus.PAYMENT_PENDING) {
            paymentStatus = PaymentStatus.PENDING;
        }

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
                .scheduledAt(req.getScheduledAt())
                .paymentAmount(req.getPaymentAmount())
                .paymentStatus(paymentStatus)
                .nextStepInstruction(instruction)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }
}
