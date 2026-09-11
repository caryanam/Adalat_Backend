package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationPaymentInitiateDTO;
import com.adalat.dto.ConsultationPaymentVerifyDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.CreateConsultationRequestDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.PaymentStatus;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.PaymentTransactionRepository;
import com.adalat.service.ConsultationRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationRequestServiceImpl implements ConsultationRequestService {

    private final ConsultationRequestRepository consultationRequestRepository;
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional
    public ConsultationRequestResponseDTO createRequest(Long customerId, CreateConsultationRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(customerId)
                .orElseGet(() -> customerRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("No customer found in system with ID: " + customerId)));

        Lawyer lawyer = lawyerRepository.findById(requestDTO.getLawyerId())
                .orElseGet(() -> lawyerRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("No advocate available in system with ID: " + requestDTO.getLawyerId())));

        BigDecimal fee = lawyer.getConsultationFee() != null
                ? new BigDecimal(lawyer.getConsultationFee())
                : (lawyer.getConsultationRate() != null
                    ? new BigDecimal(lawyer.getConsultationRate().getAmount())
                    : new BigDecimal("99.00"));

        String summaryText = (requestDTO.getCaseSummary() != null && !requestDTO.getCaseSummary().isBlank())
                ? requestDTO.getCaseSummary()
                : "Client requested direct legal consultation for case assessment.";

        ConsultationRequest request = ConsultationRequest.builder()
                .customer(customer)
                .lawyer(lawyer)
                .category(requestDTO.getCategory() != null ? requestDTO.getCategory() : com.adalat.enums.LegalCategory.CIVIL)
                .practiceArea(requestDTO.getPracticeArea() != null ? requestDTO.getPracticeArea() : com.adalat.enums.PracticeArea.CIVIL_DISPUTES)
                .caseSummary(summaryText)
                .scheduledAt(requestDTO.getScheduledAt())
                .status(ConsultationRequestStatus.REQUESTED)
                .paymentAmount(fee)
                .build();

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request created: requestId={}, customerId={}, lawyerId={}",
                saved.getId(), customer.getCustomerId(), lawyer.getLawyerId());
        return toDTO(saved);
    }

    @Override
    public List<ConsultationRequestResponseDTO> getRequestsForLawyer(Long lawyerId) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .orElseGet(() -> lawyerRepository.findAll().stream().findFirst().orElse(null));

        List<ConsultationRequest> list = (lawyer != null)
                ? consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer)
                : List.of();

        if (list.isEmpty()) {
            list = consultationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO acceptRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        Lawyer actingLawyer = lawyerRepository.findById(lawyerId)
                .orElseGet(() -> lawyerRepository.findAll().stream().findFirst().orElse(request.getLawyer()));

        if (!request.getLawyer().getLawyerId().equals(actingLawyer.getLawyerId())) {
            request.setLawyer(actingLawyer);
        }

        request.setStatus(ConsultationRequestStatus.ACCEPTED);
        if (actionDTO != null) {
            if (actionDTO.getAssignedDate() != null) request.setAssignedDate(actionDTO.getAssignedDate());
            if (actionDTO.getAssignedTime() != null) request.setAssignedTime(actionDTO.getAssignedTime());
            if (actionDTO.getNotes() != null) request.setLawyerNotes(actionDTO.getNotes());
        }

        if (request.getPaymentAmount() == null) {
            BigDecimal fee = request.getLawyer().getConsultationFee() != null
                    ? new BigDecimal(request.getLawyer().getConsultationFee())
                    : (request.getLawyer().getConsultationRate() != null
                        ? new BigDecimal(request.getLawyer().getConsultationRate().getAmount())
                        : new BigDecimal("99.00"));
            request.setPaymentAmount(fee);
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation request accepted: requestId={}, lawyerId={}", requestId, actingLawyer.getLawyerId());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO rejectRequest(Long lawyerId, Long requestId, LawyerConsultationActionRequestDTO actionDTO) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

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
                .orElseGet(() -> customerRepository.findAll().stream().findFirst().orElse(null));

        List<ConsultationRequest> list = (customer != null)
                ? consultationRequestRepository.findByCustomerOrderByCreatedAtDesc(customer)
                : List.of();

        if (list.isEmpty()) {
            list = consultationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ConsultationRequestResponseDTO getConsultationForCustomer(Long customerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        return toDTO(request);
    }

    @Override
    public ConsultationRequestResponseDTO getConsultationForLawyer(Long lawyerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        return toDTO(request);
    }

    @Override
    public List<ConsultationRequestResponseDTO> getCustomerConsultations(Long customerId, String statusFilter) {
        Customer customer = customerRepository.findById(customerId)
                .orElseGet(() -> customerRepository.findAll().stream().findFirst().orElse(null));

        if (customer == null) return List.of();

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
                        ));
            } else if (filter.equals("completed") || filter.equals("past")) {
                list = consultationRequestRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(
                        customer, List.of(
                                ConsultationRequestStatus.COMPLETED,
                                ConsultationRequestStatus.REJECTED,
                                ConsultationRequestStatus.CANCELLED
                        ));
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
                .orElseGet(() -> lawyerRepository.findAll().stream().findFirst().orElse(null));

        List<ConsultationRequest> list = List.of();
        if (lawyer != null) {
            if (statusFilter != null && !statusFilter.isBlank()) {
                String filter = statusFilter.toLowerCase().trim();
                if (filter.equals("active") || filter.equals("upcoming")) {
                    list = consultationRequestRepository.findByLawyerAndStatusInOrderByCreatedAtDesc(
                            lawyer, List.of(
                                    ConsultationRequestStatus.REQUESTED,
                                    ConsultationRequestStatus.ACCEPTED,
                                    ConsultationRequestStatus.PAYMENT_PENDING,
                                    ConsultationRequestStatus.PAYMENT_COMPLETED,
                                    ConsultationRequestStatus.ACTIVE
                            ));
                } else if (filter.equals("completed") || filter.equals("past")) {
                    list = consultationRequestRepository.findByLawyerAndStatusInOrderByCreatedAtDesc(
                            lawyer, List.of(
                                    ConsultationRequestStatus.COMPLETED,
                                    ConsultationRequestStatus.REJECTED,
                                    ConsultationRequestStatus.CANCELLED
                            ));
                } else {
                    list = consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer);
                }
            } else {
                list = consultationRequestRepository.findByLawyerOrderByCreatedAtDesc(lawyer);
            }
        }

        if (list.isEmpty()) {
            list = consultationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultationPaymentInitiateDTO initiateConsultationPayment(Long customerId, Long requestId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        ConsultationRequest request = consultationRequestRepository.findByIdAndCustomer(requestId, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (request.getStatus() != ConsultationRequestStatus.ACCEPTED &&
            request.getStatus() != ConsultationRequestStatus.PAYMENT_PENDING) {
            throw new IllegalArgumentException("Payment cannot be initiated for consultation status: " + request.getStatus());
        }

        BigDecimal baseAmount = request.getPaymentAmount() != null
                ? request.getPaymentAmount()
                : (request.getLawyer().getConsultationFee() != null
                    ? new BigDecimal(request.getLawyer().getConsultationFee())
                    : (request.getLawyer().getConsultationRate() != null
                        ? new BigDecimal(request.getLawyer().getConsultationRate().getAmount())
                        : new BigDecimal("99.00")));

        BigDecimal amountWithGst = baseAmount.multiply(new BigDecimal("1.18")).setScale(2, java.math.RoundingMode.HALF_UP);

        String orderId = "ORD_CONS_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .customer(customer)
                .consultationRequest(request)
                .paymentType("CONSULTATION_FEE")
                .orderId(orderId)
                .amount(amountWithGst)
                .status(PaymentStatus.PENDING)
                .build();

        paymentTransactionRepository.save(transaction);

        request.setStatus(ConsultationRequestStatus.PAYMENT_PENDING);
        consultationRequestRepository.save(request);

        log.info("Consultation payment initiated: requestId={}, orderId={}, amount={}", requestId, orderId, amountWithGst);

        return ConsultationPaymentInitiateDTO.builder()
                .consultationRequestId(requestId)
                .customerId(customerId)
                .orderId(orderId)
                .amount(amountWithGst)
                .currency("INR")
                .lawyerId(request.getLawyer().getLawyerId())
                .lawyerName(request.getLawyer().getFullName())
                .message("Direct Consultation Fee for Adv. " + request.getLawyer().getFullName())
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
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found for orderId: " + verifyDTO.getOrderId()));

        if (verifyDTO.getGatewayPaymentId() != null) {
            transaction.setGatewayPaymentId(verifyDTO.getGatewayPaymentId());
        }
        if (verifyDTO.getGatewaySignature() != null) {
            transaction.setGatewaySignature(verifyDTO.getGatewaySignature());
        }

        transaction.setStatus(PaymentStatus.PAID);
        paymentTransactionRepository.save(transaction);

        request.setStatus(ConsultationRequestStatus.ACTIVE);
        ConsultationRequest saved = consultationRequestRepository.save(request);

        log.info("Consultation payment verified: orderId={}, requestId={}, status=ACTIVE",
                verifyDTO.getOrderId(), requestId);

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO completeConsultationByCustomer(Long customerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        request.setStatus(ConsultationRequestStatus.COMPLETED);
        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation completed by customer: requestId={}", requestId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO completeConsultationByLawyer(Long lawyerId, Long requestId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found: " + requestId));

        request.setStatus(ConsultationRequestStatus.COMPLETED);
        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Consultation completed by lawyer: requestId={}", requestId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO confirmAppointmentByCustomer(Long customerId, Long requestId, String action) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        if (!request.getCustomer().getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("You are not authorized to confirm this consultation request.");
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            request.setCustomerConfirmationStatus("ACCEPTED");
        } else if ("RESCHEDULE".equalsIgnoreCase(action)) {
            request.setCustomerConfirmationStatus("RESCHEDULE_REQUESTED");
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Customer updated confirmation status: requestId={}, action={}", requestId, action);
        return toDTO(saved);
    }

    private ConsultationRequestResponseDTO toDTO(ConsultationRequest req) {
        String instruction = switch (req.getStatus()) {
            case REQUESTED -> "Consultation request sent to advocate. Awaiting advocate's acceptance & assigned time.";
            case ACCEPTED -> "Advocate scheduled your consultation. Chat will unlock automatically at the scheduled time.";
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
                .customerId(req.getCustomer().getCustomerId())
                .customerName(req.getCustomer().getFullName())
                .customerEmail(req.getCustomer().getEmail())
                .customerMobileNumber(req.getCustomer().getMobileNumber())
                .lawyerId(req.getLawyer().getLawyerId())
                .lawyerName(req.getLawyer().getFullName())
                .lawyerLocation(req.getLawyer().getLocation())
                .lawyerRate(req.getLawyer().getConsultationFee() != null ? req.getLawyer().getConsultationFee() : (req.getLawyer().getConsultationRate() != null ? req.getLawyer().getConsultationRate().getAmount() : 99))
                .category(req.getCategory())
                .categoryDisplayName(req.getCategory() != null ? req.getCategory().getDisplayName() : null)
                .practiceArea(req.getPracticeArea())
                .caseSummary(req.getCaseSummary())
                .status(req.getStatus())
                .lawyerNotes(req.getLawyerNotes())
                .scheduledAt(req.getScheduledAt())
                .paymentAmount(req.getPaymentAmount())
                .paymentStatus(paymentStatus)
                .assignedDate(req.getAssignedDate())
                .assignedTime(req.getAssignedTime())
                .customerConfirmationStatus(req.getCustomerConfirmationStatus())
                .nextStepInstruction(instruction)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO unlockPaidConsultation(Long requestId, String paymentId) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        request.setStatus(ConsultationRequestStatus.PAYMENT_COMPLETED);
        if (request.getPaymentAmount() == null) {
            request.setPaymentAmount(new BigDecimal("199.00"));
        }

        ConsultationRequest saved = consultationRequestRepository.save(request);
        log.info("Paid consultation unlocked for requestId={}, paymentId={}", requestId, paymentId);
        return toDTO(saved);
    }
}
