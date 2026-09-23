package com.adalat.serviceImpl;

import com.adalat.dto.ConsultationPaymentInitiateDTO;
import com.adalat.dto.ConsultationPaymentVerifyDTO;
import com.adalat.dto.ConsultationRequestResponseDTO;
import com.adalat.dto.CreateConsultationRequestDTO;
import com.adalat.dto.LawyerConsultationActionRequestDTO;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.DocumentType;
import com.adalat.enums.PaymentStatus;
import com.adalat.enums.Role;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerDocumentRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.repository.PaymentTransactionRepository;
import com.adalat.service.ConsultationRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final com.adalat.service.NotificationService notificationService;

    @Override
    @Transactional
    public ConsultationRequestResponseDTO createRequest(Long customerId, CreateConsultationRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(customerId)
                .orElseGet(() -> customerRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("No customer found in system with ID: " + customerId)));

        Lawyer lawyer = lawyerRepository.findById(requestDTO.getLawyerId())
                .orElseThrow(() -> new ResourceNotFoundException("No advocate found in system with ID: " + requestDTO.getLawyerId()));

        if (lawyer.getVerificationStatus() != com.adalat.enums.VerificationStatus.APPROVED) {
            throw new com.adalat.exception.LawyerNotApprovedException("This advocate application is pending verification or not yet approved by Admin.");
        }

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

        // Dispatch notifications
        try {
            // 1. Lawyer Notification: New Consultation Request
            notificationService.createNotification(
                    Role.LAWYER,
                    lawyer.getLawyerId(),
                    "New Consultation Request",
                    "Client " + customer.getFullName() + " requested a consultation for " + (saved.getCategory() != null ? saved.getCategory().getDisplayName() : "Legal Matter") + ".",
                    com.adalat.enums.NotificationType.NEW_CONSULTATION_REQUEST,
                    saved.getId(),
                    "CONSULTATION",
                    "/lawyer/requests"
            );

            // 2. Customer Notification: Request Submitted
            notificationService.createNotification(
                    Role.CUSTOMER,
                    customer.getCustomerId(),
                    "Consultation Request Sent",
                    "Your consultation request has been sent to Adv. " + lawyer.getFullName() + ". Awaiting advocate confirmation.",
                    com.adalat.enums.NotificationType.SYSTEM_ALERT,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/consultations"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch createRequest notifications: {}", notifEx.getMessage());
        }

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

        // Dispatch notifications
        try {
            // Customer Notification: Accepted
            String timeMsg = (actionDTO != null && actionDTO.getAssignedDate() != null)
                    ? " Scheduled for " + actionDTO.getAssignedDate() + " at " + actionDTO.getAssignedTime() + "."
                    : " Consultation is scheduled.";

            notificationService.createNotification(
                    Role.CUSTOMER,
                    request.getCustomer().getCustomerId(),
                    "Consultation Request Accepted!",
                    "Adv. " + actingLawyer.getFullName() + " accepted your consultation request." + timeMsg + " Please complete payment to start.",
                    com.adalat.enums.NotificationType.CONSULTATION_ACCEPTED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/consultations"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch acceptRequest notification: {}", notifEx.getMessage());
        }

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

        // Dispatch notifications
        try {
            // Customer Notification: Rejected
            String noteMsg = (actionDTO != null && actionDTO.getNotes() != null && !actionDTO.getNotes().isBlank())
                    ? " Note from advocate: " + actionDTO.getNotes()
                    : "";

            notificationService.createNotification(
                    Role.CUSTOMER,
                    request.getCustomer().getCustomerId(),
                    "Consultation Request Declined",
                    "Adv. " + request.getLawyer().getFullName() + " was unable to accept your request." + noteMsg,
                    com.adalat.enums.NotificationType.CONSULTATION_REJECTED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/consultations"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch rejectRequest notification: {}", notifEx.getMessage());
        }

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

        // Dispatch notifications
        try {
            BigDecimal feeAmt = saved.getPaymentAmount() != null ? saved.getPaymentAmount() : new BigDecimal("99.00");

            // 1. Customer Notification: Payment Confirmed
            notificationService.createNotification(
                    Role.CUSTOMER,
                    saved.getCustomer().getCustomerId(),
                    "Payment Confirmed • Consultation Active",
                    "Payment of ₹" + feeAmt + " confirmed for consultation with Adv. " + saved.getLawyer().getFullName() + ". Chat room is now active.",
                    com.adalat.enums.NotificationType.PAYMENT_CONFIRMED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/chat/" + saved.getId()
            );

            // 2. Lawyer Notification: Payment Received
            notificationService.createNotification(
                    Role.LAWYER,
                    saved.getLawyer().getLawyerId(),
                    "Consultation Fee Received",
                    "Client " + saved.getCustomer().getFullName() + " completed payment of ₹" + feeAmt + ". The consultation session is active.",
                    com.adalat.enums.NotificationType.CONSULTATION_PAID,
                    saved.getId(),
                    "CONSULTATION",
                    "/lawyer/chat/" + saved.getId()
            );

            // 3. Admin Notification: Consultation Payment
            notificationService.createNotification(
                    Role.ADMIN,
                    null,
                    "Consultation Fee Paid",
                    "Consultation fee ₹" + feeAmt + " received for Request #" + saved.getId() + " (Client: " + saved.getCustomer().getFullName() + ", Advocate: " + saved.getLawyer().getFullName() + ").",
                    com.adalat.enums.NotificationType.CONSULTATION_PAYMENT_LOGGED,
                    saved.getId(),
                    "PAYMENT",
                    "/admin/payments"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch verifyConsultationPayment notifications: {}", notifEx.getMessage());
        }

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

        // Dispatch notifications
        try {
            notificationService.createNotification(
                    Role.CUSTOMER,
                    saved.getCustomer().getCustomerId(),
                    "Consultation Concluded",
                    "Your consultation with Adv. " + saved.getLawyer().getFullName() + " has completed. Please leave a rating and review.",
                    com.adalat.enums.NotificationType.CONSULTATION_COMPLETED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/consultations"
            );

            notificationService.createNotification(
                    Role.LAWYER,
                    saved.getLawyer().getLawyerId(),
                    "Consultation Concluded",
                    "Consultation with client " + saved.getCustomer().getFullName() + " has concluded.",
                    com.adalat.enums.NotificationType.CONSULTATION_COMPLETED,
                    saved.getId(),
                    "CONSULTATION",
                    "/lawyer/requests"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch completeConsultation notifications: {}", notifEx.getMessage());
        }

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

        // Dispatch notifications
        try {
            notificationService.createNotification(
                    Role.CUSTOMER,
                    saved.getCustomer().getCustomerId(),
                    "Consultation Concluded",
                    "Adv. " + saved.getLawyer().getFullName() + " marked your consultation as completed. Please share your rating & review.",
                    com.adalat.enums.NotificationType.CONSULTATION_COMPLETED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/consultations"
            );

            notificationService.createNotification(
                    Role.LAWYER,
                    saved.getLawyer().getLawyerId(),
                    "Consultation Concluded",
                    "Consultation with client " + saved.getCustomer().getFullName() + " has concluded.",
                    com.adalat.enums.NotificationType.CONSULTATION_COMPLETED,
                    saved.getId(),
                    "CONSULTATION",
                    "/lawyer/requests"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch completeConsultationByLawyer notifications: {}", notifEx.getMessage());
        }

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
        boolean isPaid = paymentTransactionRepository.findByConsultationRequestAndStatus(req, PaymentStatus.PAID).isPresent();
        if (isPaid) {
            paymentStatus = PaymentStatus.PAID;
        } else if (req.getStatus() == ConsultationRequestStatus.PAYMENT_PENDING) {
            paymentStatus = PaymentStatus.PENDING;
        }

        Boolean isFreeChatTimeOver = false;
        long remainingSeconds = 120;
        if (req.getChatStartedAt() != null) {
            long elapsedSeconds = Duration.between(req.getChatStartedAt(), LocalDateTime.now()).getSeconds();
            remainingSeconds = Math.max(0, 120 - elapsedSeconds);
            isFreeChatTimeOver = elapsedSeconds >= 120;
        }

        // Retrieve Lawyer profile photo URL
        String lawyerPhotoUrl = req.getLawyer().getProfilePhotoUrl();
        if ((lawyerPhotoUrl == null || lawyerPhotoUrl.isBlank()) && lawyerDocumentRepository != null) {
            List<LawyerDocument> docs = lawyerDocumentRepository.findByLawyer(req.getLawyer());
            if (docs != null && !docs.isEmpty()) {
                lawyerPhotoUrl = docs.stream()
                        .filter(d -> d.getDocumentType() == DocumentType.PHOTO ||
                                     (d.getFileUrl() != null && d.getFileUrl().toLowerCase().matches(".*\\.(jpg|jpeg|png|webp|gif)$")))
                        .map(LawyerDocument::getFileUrl)
                        .findFirst()
                        .orElse(null);
            }
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
                .lawyerProfileImageUrl(lawyerPhotoUrl)
                .lawyerUpiId(req.getLawyer().getUpiId() != null && !req.getLawyer().getUpiId().isBlank() ? req.getLawyer().getUpiId() : "advocate@upi")
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
                .chatStartedAt(req.getChatStartedAt())
                .isFreeChatTimeOver(isFreeChatTimeOver)
                .remainingSeconds(remainingSeconds)
                .assignedDate(req.getAssignedDate())
                .assignedTime(req.getAssignedTime())
                .customerConfirmationStatus(req.getCustomerConfirmationStatus())
                .nextStepInstruction(instruction)
                .rating(req.getRating())
                .ratingComment(req.getRatingComment())
                .ratedAt(req.getRatedAt())
                .lawyerRating(req.getLawyer().getRating() != null ? req.getLawyer().getRating() : 0.0)
                .lawyerRatingCount(req.getLawyer().getRatingCount() != null ? req.getLawyer().getRatingCount() : 0)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO unlockPaidConsultation(Long requestId, String paymentId) {
        return unlockPaidConsultation(requestId, paymentId, null);
    }

    @Override
    @Transactional
    public ConsultationRequestResponseDTO unlockPaidConsultation(Long requestId, String paymentId, String amountStr) {
        ConsultationRequest request = consultationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation request not found: " + requestId));

        BigDecimal fee = null;
        if (amountStr != null && !amountStr.isBlank()) {
            try {
                fee = new BigDecimal(amountStr.replace("₹", "").replace(",", "").trim());
            } catch (Exception e) {
                fee = null;
            }
        }

        if (fee == null) {
            fee = request.getPaymentAmount() != null
                    ? request.getPaymentAmount()
                    : (request.getLawyer().getConsultationFee() != null
                        ? new BigDecimal(request.getLawyer().getConsultationFee())
                        : (request.getLawyer().getConsultationRate() != null
                            ? new BigDecimal(request.getLawyer().getConsultationRate().getAmount())
                            : new BigDecimal("199.00")));
        }

        request.setPaymentAmount(fee);
        request.setStatus(ConsultationRequestStatus.PAYMENT_COMPLETED);
        ConsultationRequest saved = consultationRequestRepository.save(request);

        // Record PaymentTransaction
        String cleanOrderId = "ORD_CONS_" + saved.getId() + "_" + (paymentId != null && !paymentId.isBlank() ? paymentId : System.currentTimeMillis());
        PaymentTransaction transaction = paymentTransactionRepository.findByConsultationRequestAndStatus(saved, PaymentStatus.PAID)
                .orElseGet(() -> {
                    List<PaymentTransaction> list = paymentTransactionRepository.findByConsultationRequest(saved);
                    if (!list.isEmpty()) {
                        return list.get(0);
                    }
                    return PaymentTransaction.builder()
                            .customer(saved.getCustomer())
                            .consultationRequest(saved)
                            .orderId(cleanOrderId)
                            .paymentType("CONSULTATION_FEE")
                            .build();
                });

        transaction.setCustomer(saved.getCustomer());
        transaction.setConsultationRequest(saved);
        transaction.setAmount(fee);
        transaction.setPaymentType("CONSULTATION_FEE");
        transaction.setStatus(PaymentStatus.PAID);
        if (paymentId != null && !paymentId.isBlank()) {
            transaction.setGatewayPaymentId(paymentId);
        }
        paymentTransactionRepository.save(transaction);

        log.info("Paid consultation unlocked for requestId={}, paymentId={}, amount={}", requestId, paymentId, fee);

        // Dispatch notifications
        try {
            // 1. Customer Notification
            notificationService.createNotification(
                    Role.CUSTOMER,
                    saved.getCustomer().getCustomerId(),
                    "Payment Confirmed • Consultation Active",
                    "Payment of ₹" + fee + " confirmed for consultation with Adv. " + saved.getLawyer().getFullName() + ". Chat room is active.",
                    com.adalat.enums.NotificationType.PAYMENT_CONFIRMED,
                    saved.getId(),
                    "CONSULTATION",
                    "/customer/chat/" + saved.getId()
            );

            // 2. Lawyer Notification
            notificationService.createNotification(
                    Role.LAWYER,
                    saved.getLawyer().getLawyerId(),
                    "Consultation Fee Received",
                    "Client " + saved.getCustomer().getFullName() + " completed payment of ₹" + fee + ". The consultation session is active.",
                    com.adalat.enums.NotificationType.CONSULTATION_PAID,
                    saved.getId(),
                    "CONSULTATION",
                    "/lawyer/chat/" + saved.getId()
            );

            // 3. Admin Notification
            notificationService.createNotification(
                    Role.ADMIN,
                    null,
                    "Consultation Fee Paid",
                    "Consultation fee ₹" + fee + " received for Request #" + saved.getId() + " (Client: " + saved.getCustomer().getFullName() + ", Advocate: " + saved.getLawyer().getFullName() + ").",
                    com.adalat.enums.NotificationType.CONSULTATION_PAYMENT_LOGGED,
                    saved.getId(),
                    "PAYMENT",
                    "/admin/payments"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch unlockPaidConsultation notifications: {}", notifEx.getMessage());
        }

        return toDTO(saved);
    }
}
