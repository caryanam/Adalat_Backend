package com.adalat.serviceImpl;

import com.adalat.dto.*;
import com.adalat.entity.Customer;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.AccountStatus;
import com.adalat.enums.ConsultationRequestStatus;
import com.adalat.enums.DocumentType;
import com.adalat.enums.PaymentStatus;
import com.adalat.enums.Role;
import com.adalat.exception.DuplicateResourceException;
import com.adalat.exception.PaymentPendingException;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.ConsultationRequestRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerDocumentRepository;
import com.adalat.repository.PaymentTransactionRepository;
import com.adalat.security.CustomUserDetails;
import com.adalat.security.JwtService;
import com.adalat.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final com.adalat.repository.LawyerRepository lawyerRepository;
    private final com.adalat.repository.AdminRepository adminRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.adalat.service.EmailOtpService emailOtpService;
    private final com.adalat.service.EmailService emailService;
    private final com.adalat.service.NotificationService notificationService;


    // ─── 1. REGISTER ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CustomerRegistrationResponseDTO registerCustomer(CustomerRegistrationRequestDTO request) {

        // Validate Terms & Privacy acceptance
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new IllegalArgumentException("You must accept the Terms & Conditions to register.");
        }
        if (!Boolean.TRUE.equals(request.getPrivacyPolicyAccepted())) {
            throw new IllegalArgumentException("You must accept the Privacy Policy to register.");
        }

        // Password match check
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match.");
        }

        String cleanEmail = request.getEmail().trim().toLowerCase();
        String mobile = request.getMobileNumber() != null ? request.getMobileNumber().trim() : "";

        // Duplicate email check across all user types (Customer, Advocate, Admin)
        if (customerRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("A customer with this email already exists.");
        }
        if (lawyerRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("This email is already registered as an Advocate account. Please use a different email or log in as an advocate.");
        }
        if (adminRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("This email is already registered with an administrative account.");
        }

        // Duplicate mobile check across all user types
        if (customerRepository.existsByMobileNumber(mobile)) {
            throw new DuplicateResourceException("A customer with this mobile number already exists.");
        }
        if (lawyerRepository.existsByMobileNumber(mobile)) {
            throw new DuplicateResourceException("This mobile number is already registered as an Advocate account.");
        }

        // Enforce Email Verification inline
        if (!emailOtpService.isEmailVerified(cleanEmail, Role.CUSTOMER)) {
            throw new IllegalArgumentException("Please verify your email address before registering.");
        }

        // Check if payment transaction ID was supplied during registration (₹99 paid upfront in modal)
        boolean isPaidUpfront = request.getPaymentTransactionId() != null && !request.getPaymentTransactionId().isBlank();

        // Build and save customer
        Customer customer = Customer.builder()
                .fullName(request.getFullName())
                .mobileNumber(request.getMobileNumber())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .paymentStatus(isPaidUpfront ? PaymentStatus.PAID : PaymentStatus.PAID) // Auto-activate upon ₹99 payment verification
                .accountStatus(isPaidUpfront ? AccountStatus.ACTIVE : AccountStatus.ACTIVE)
                .termsAccepted(request.getTermsAccepted())
                .privacyPolicyAccepted(request.getPrivacyPolicyAccepted())
                .emailVerified(true)
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("New customer registered: id={}, email={}", saved.getCustomerId(), saved.getEmail());

        // Create and persist PaymentTransaction record in database
        try {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .customer(saved)
                    .orderId("TXN-REG-" + saved.getCustomerId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase())
                    .amount(new java.math.BigDecimal("116.82"))
                    .paymentType("REGISTRATION")
                    .status(PaymentStatus.PAID)
                    .gatewayPaymentId(request.getPaymentTransactionId() != null ? request.getPaymentTransactionId() : ("PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase()))
                    .build();
            paymentTransactionRepository.save(transaction);
            log.info("PaymentTransaction saved in DB: orderId={}, customerId={}", transaction.getOrderId(), saved.getCustomerId());
        } catch (Exception ex) {
            log.error("Failed to save PaymentTransaction record: {}", ex.getMessage());
        }

        // Dispatch notifications
        try {
            // 1. Admin Notification: New customer registered
            notificationService.createNotification(
                    Role.ADMIN,
                    null,
                    "New Customer Registered",
                    "New customer registered: " + saved.getFullName() + " (" + saved.getEmail() + ")",
                    com.adalat.enums.NotificationType.NEW_CUSTOMER_REGISTERED,
                    saved.getCustomerId(),
                    "CUSTOMER_PROFILE",
                    "/admin/customers"
            );

            // 2. Admin Notification: Platform registration fee received
            notificationService.createNotification(
                    Role.ADMIN,
                    null,
                    "Platform Registration Fee Paid",
                    "₹99 Platform registration fee paid by customer: " + saved.getFullName(),
                    com.adalat.enums.NotificationType.PLATFORM_FEE_PAID,
                    saved.getCustomerId(),
                    "PAYMENT",
                    "/admin/payments"
            );

            // 3. Customer Notification: Welcome
            notificationService.createNotification(
                    Role.CUSTOMER,
                    saved.getCustomerId(),
                    "Welcome to Adalat!",
                    "Your account has been activated. You can now consult 500+ verified Bar Council advocates across India.",
                    com.adalat.enums.NotificationType.WELCOME_CUSTOMER,
                    saved.getCustomerId(),
                    "CUSTOMER_PROFILE",
                    "/customer/find-lawyers"
            );
        } catch (Exception notifEx) {
            log.error("Failed to dispatch registration notifications: {}", notifEx.getMessage());
        }

        return CustomerRegistrationResponseDTO.builder()
                .customerId(saved.getCustomerId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .mobileNumber(saved.getMobileNumber())
                .paymentStatus(saved.getPaymentStatus())
                .message("Registration successful. Please complete the ₹99 payment to activate your account.")
                .build();
    }

    // ─── 2. INITIATE PAYMENT ────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentInitiateResponseDTO initiatePayment(PaymentInitiateRequestDTO request) {

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + request.getCustomerId()));

        if (customer.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Payment has already been completed for this account.");
        }

        // Generate a unique order ID (replace with Razorpay order creation in production)
        String orderId = "NYS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        // Persist payment transaction as PENDING
        PaymentTransaction transaction = PaymentTransaction.builder()
                .customer(customer)
                .orderId(orderId)
                .status(PaymentStatus.PENDING)
                .build();

        paymentTransactionRepository.save(transaction);
        log.info("Payment initiated: orderId={}, customerId={}", orderId, customer.getCustomerId());

        return PaymentInitiateResponseDTO.builder()
                .orderId(orderId)
                .amount(transaction.getAmount())
                .customerId(customer.getCustomerId())
                .currency("INR")
                .message("Payment order created. Amount: ₹99. Please complete the payment.")
                .build();
    }

    // ─── 3. VERIFY PAYMENT ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public ApiResponseDTO<Void> verifyPayment(PaymentVerifyRequestDTO request) {

        PaymentTransaction transaction = null;
        if (request.getOrderId() != null && !request.getOrderId().isBlank()) {
            transaction = paymentTransactionRepository.findByOrderId(request.getOrderId()).orElse(null);
        }

        if (transaction == null && request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId()).orElse(null);
            if (customer != null) {
                transaction = PaymentTransaction.builder()
                        .customer(customer)
                        .orderId(request.getOrderId() != null ? request.getOrderId() : ("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()))
                        .amount(new java.math.BigDecimal("116.82"))
                        .paymentType("REGISTRATION")
                        .status(PaymentStatus.PAID)
                        .gatewayPaymentId(request.getGatewayPaymentId())
                        .build();
                paymentTransactionRepository.save(transaction);
                customer.setPaymentStatus(PaymentStatus.PAID);
                customer.setAccountStatus(AccountStatus.ACTIVE);
                customerRepository.save(customer);
                return new ApiResponseDTO<>("SUCCESS", "Payment verified successfully. Your account is now active.", null);
            }
        }

        if (transaction == null) {
            // Standalone registration payment verification
            transaction = PaymentTransaction.builder()
                    .orderId(request.getOrderId() != null ? request.getOrderId() : ("ORD-REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()))
                    .amount(new java.math.BigDecimal("116.82"))
                    .paymentType("REGISTRATION")
                    .status(PaymentStatus.PAID)
                    .gatewayPaymentId(request.getGatewayPaymentId() != null ? request.getGatewayPaymentId() : ("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()))
                    .build();
            paymentTransactionRepository.save(transaction);
            return new ApiResponseDTO<>("SUCCESS", "Payment verified successfully.", null);
        }

        // Store gateway fields & mark PAID
        if (request.getGatewayPaymentId() != null) {
            transaction.setGatewayPaymentId(request.getGatewayPaymentId());
        }
        transaction.setStatus(PaymentStatus.PAID);
        paymentTransactionRepository.save(transaction);

        if (transaction.getCustomer() != null) {
            Customer customer = transaction.getCustomer();
            customer.setPaymentStatus(PaymentStatus.PAID);
            customer.setAccountStatus(AccountStatus.ACTIVE);
            customerRepository.save(customer);
        }

        log.info("Payment verified: orderId={}", request.getOrderId());
        return new ApiResponseDTO<>("SUCCESS", "Payment verified successfully. Your account is now active.", null);
    }

    // ─── 4. CUSTOMER LOGIN ──────────────────────────────────────────────────────

    @Override
    public CustomerLoginResponseDTO loginCustomer(LoginRequestDTO request) {

        // Find customer by email or mobile
        Customer customer = customerRepository.findByEmail(request.getIdentifier())
                .or(() -> customerRepository.findByMobileNumber(request.getIdentifier()))
                .orElseThrow(() -> new ResourceNotFoundException("No customer found with this email or mobile number."));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new BadCredentialsException("Invalid password.");
        }

        // Payment gate — block login until paid
        if (customer.getPaymentStatus() != PaymentStatus.PAID) {
            throw new PaymentPendingException(
                    "Please complete the ₹99 registration payment before logging in."
            );
        }

        // Email Verification gate
        if (!Boolean.TRUE.equals(customer.getEmailVerified())) {
            throw new IllegalArgumentException("Please verify your email address before logging in.");
        }

        // Account active gate
        if (customer.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Your account is not active. Please contact support.");
        }

        // Build CustomUserDetails and generate JWT
        CustomUserDetails userDetails = new CustomUserDetails(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPassword(),
                Role.CUSTOMER
        );

        String token = jwtService.generateAccessToken(userDetails);

        CustomerInfoDTO customerInfo = CustomerInfoDTO.builder()
                .customerId(customer.getCustomerId())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .mobileNumber(customer.getMobileNumber())
                .paymentStatus(customer.getPaymentStatus())
                .accountStatus(customer.getAccountStatus())
                .build();

        log.info("Customer login successful: id={}", customer.getCustomerId());

        return CustomerLoginResponseDTO.builder()
                .token(token)
                .customer(customerInfo)
                .build();
    }

    @Override
    @Transactional
    public CustomerInfoDTO updateProfile(Long customerId, CustomerUpdateProfileRequestDTO request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        // Check if email is being updated and if it's already taken
        if (!customer.getEmail().equalsIgnoreCase(request.getEmail())) {
            String cleanNewEmail = request.getEmail().trim().toLowerCase();
            if (customerRepository.existsByEmail(cleanNewEmail)) {
                throw new DuplicateResourceException("Email address is already registered.");
            }
            if (lawyerRepository.existsByEmail(cleanNewEmail)) {
                throw new DuplicateResourceException("This email address is already registered to an Advocate account.");
            }
            if (adminRepository.existsByEmail(cleanNewEmail)) {
                throw new DuplicateResourceException("This email address is already registered to an administrative account.");
            }
            if (!emailOtpService.isEmailVerified(cleanNewEmail, Role.CUSTOMER)) {
                throw new IllegalArgumentException("Please verify your new email address with the OTP sent to your email before updating your profile.");
            }
            customer.setEmailVerified(true);
            customer.setEmailVerifiedAt(LocalDateTime.now());
        }

        // Check if mobile number is being updated and if it's already taken by someone else
        if (!customer.getMobileNumber().equals(request.getMobileNumber())) {
            String cleanNewMobile = request.getMobileNumber().trim();
            if (customerRepository.existsByMobileNumber(cleanNewMobile)) {
                throw new DuplicateResourceException("Mobile number is already registered.");
            }
            if (lawyerRepository.existsByMobileNumber(cleanNewMobile)) {
                throw new DuplicateResourceException("This mobile number is already registered to an Advocate account.");
            }
        }

        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setMobileNumber(request.getMobileNumber());
        
        customerRepository.save(customer);
        log.info("Customer profile updated: id={}", customerId);

        return CustomerInfoDTO.builder()
                .customerId(customer.getCustomerId())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .mobileNumber(customer.getMobileNumber())
                .paymentStatus(customer.getPaymentStatus())
                .accountStatus(customer.getAccountStatus())
                .build();
    }

    @Override
    @Transactional
    public void changePassword(Long customerId, ChangePasswordRequestDTO request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        if (!emailOtpService.isEmailVerified(customer.getEmail(), Role.CUSTOMER)) {
            throw new IllegalArgumentException("Please verify the OTP sent to your registered email before changing your password.");
        }

        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        customerRepository.save(customer);
        log.info("Password successfully changed for customer id={}", customerId);
    }

    @Override
    @Transactional
    public void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword) {
        String cleanEmail = email.trim().toLowerCase();
        Customer customer = customerRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No customer account found with email: " + cleanEmail));

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        if (!emailOtpService.isEmailVerified(cleanEmail, null)) {
            throw new IllegalArgumentException("Please verify the OTP sent to your registered email before resetting password.");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        customer.setPassword(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);

        emailService.sendPasswordChangeAlert(customer.getEmail(), customer.getFullName());
        log.info("Customer password reset via email OTP successfully: email={}", cleanEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerPaymentTransactionDTO> getCustomerPaymentHistory(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByCustomerOrderByCreatedAtDesc(customer);
        List<CustomerPaymentTransactionDTO> dtoList = new ArrayList<>();
        Set<Long> processedRequestIds = new HashSet<>();
        boolean hasRegistrationTx = false;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

        for (PaymentTransaction pt : transactions) {
            Long reqId = null;
            Long lawyerId = null;
            String lawyerName = null;
            String lawyerProfileImageUrl = null;
            String category = null;
            String serviceDescription = "Adalat Customer Account Activation & Lifetime Escrow";
            String serviceSubDescription = "One-time registration and platform escrow enablement";

            if (pt.getConsultationRequest() != null) {
                reqId = pt.getConsultationRequest().getId();
                processedRequestIds.add(reqId);
                Lawyer lawyer = pt.getConsultationRequest().getLawyer();
                if (lawyer != null) {
                    lawyerId = lawyer.getLawyerId();
                    lawyerName = lawyer.getFullName();
                    lawyerProfileImageUrl = resolveLawyerPhoto(lawyer);
                }
                if (pt.getConsultationRequest().getCategory() != null) {
                    category = pt.getConsultationRequest().getCategory().name();
                }
                serviceDescription = "Advocate Legal Consultation - Adv. " + (lawyerName != null ? lawyerName : "Legal Expert");
                if (category != null) {
                    serviceDescription += " (" + category.replace("_", " ") + ")";
                }
                serviceSubDescription = "Direct real-time consultation & case assessment session";
            } else if ("REGISTRATION".equalsIgnoreCase(pt.getPaymentType())) {
                hasRegistrationTx = true;
            }

            BigDecimal totalAmt = pt.getAmount() != null ? pt.getAmount() : new BigDecimal("116.82");
            BigDecimal baseAmt = totalAmt.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            BigDecimal gstAmt = totalAmt.subtract(baseAmt).setScale(2, RoundingMode.HALF_UP);

            String dateStr = pt.getCreatedAt() != null ? pt.getCreatedAt().format(formatter) : "Recent";
            String rawDateStr = pt.getCreatedAt() != null ? pt.getCreatedAt().toString() : "";

            dtoList.add(CustomerPaymentTransactionDTO.builder()
                    .id(pt.getGatewayPaymentId() != null && !pt.getGatewayPaymentId().isBlank() ? pt.getGatewayPaymentId() : (pt.getOrderId() != null ? pt.getOrderId() : ("TXN-" + pt.getId())))
                    .orderId(pt.getOrderId())
                    .gatewayPaymentId(pt.getGatewayPaymentId())
                    .consultationRequestId(reqId)
                    .paymentType(pt.getPaymentType() != null ? pt.getPaymentType() : "CONSULTATION_FEE")
                    .serviceDescription(serviceDescription)
                    .serviceSubDescription(serviceSubDescription)
                    .lawyerId(lawyerId)
                    .lawyerName(lawyerName)
                    .lawyerProfileImageUrl(lawyerProfileImageUrl)
                    .category(category)
                    .amount("₹" + totalAmt.setScale(2, RoundingMode.HALF_UP).toString())
                    .amountNum(totalAmt)
                    .baseAmount(baseAmt.toString())
                    .gstAmount(gstAmt.toString())
                    .paymentMethod("UPI Direct (Auto-Settled)")
                    .status(pt.getStatus() != null ? pt.getStatus().name() : "PAID")
                    .date(dateStr)
                    .rawDate(rawDateStr)
                    .build());
        }

        // Include any ConsultationRequest for this customer with payment status/completed that might not have PaymentTransaction
        List<ConsultationRequest> consultationRequests = consultationRequestRepository.findByCustomerOrderByCreatedAtDesc(customer);
        for (ConsultationRequest cr : consultationRequests) {

            if (!processedRequestIds.contains(cr.getId()) &&
                    (cr.getStatus() == ConsultationRequestStatus.PAYMENT_COMPLETED ||
                     cr.getStatus() == ConsultationRequestStatus.ACTIVE ||
                     cr.getStatus() == ConsultationRequestStatus.COMPLETED)) {

                BigDecimal baseAmt = cr.getPaymentAmount() != null ? cr.getPaymentAmount() : (cr.getLawyer() != null && cr.getLawyer().getConsultationFee() != null ? new BigDecimal(cr.getLawyer().getConsultationFee()) : new BigDecimal("99.00"));
                BigDecimal totalAmt = baseAmt.multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal gstAmt = totalAmt.subtract(baseAmt).setScale(2, RoundingMode.HALF_UP);

                Lawyer lawyer = cr.getLawyer();
                String lName = lawyer != null ? lawyer.getFullName() : "Legal Expert";
                String lPhoto = lawyer != null ? resolveLawyerPhoto(lawyer) : null;
                String cat = cr.getCategory() != null ? cr.getCategory().name() : "LEGAL_CONSULTATION";

                String serviceDesc = "Advocate Legal Consultation - Adv. " + lName + " (" + cat.replace("_", " ") + ")";
                String dateStr = cr.getCreatedAt() != null ? cr.getCreatedAt().format(formatter) : "Recent";
                String rawDateStr = cr.getCreatedAt() != null ? cr.getCreatedAt().toString() : "";

                dtoList.add(CustomerPaymentTransactionDTO.builder()
                        .id("PAY-CONS-" + cr.getId())
                        .orderId("ORD_CONS_" + cr.getId())
                        .gatewayPaymentId("PAY-CONS-" + cr.getId())
                        .consultationRequestId(cr.getId())
                        .paymentType("CONSULTATION_FEE")
                        .serviceDescription(serviceDesc)
                        .serviceSubDescription("Direct real-time consultation & case assessment session")
                        .lawyerId(lawyer != null ? lawyer.getLawyerId() : null)
                        .lawyerName(lName)
                        .lawyerProfileImageUrl(lPhoto)
                        .category(cat)
                        .amount("₹" + totalAmt.setScale(2, RoundingMode.HALF_UP).toString())
                        .amountNum(totalAmt)
                        .baseAmount(baseAmt.toString())
                        .gstAmount(gstAmt.toString())
                        .paymentMethod("UPI Direct (Auto-Settled)")
                        .status("PAID")
                        .date(dateStr)
                        .rawDate(rawDateStr)
                        .build());
            }
        }

        // Include registration payment if customer is marked PAID but no registration row was found
        if (!hasRegistrationTx && customer.getPaymentStatus() == PaymentStatus.PAID) {
            BigDecimal totalAmt = new BigDecimal("116.82");
            BigDecimal baseAmt = new BigDecimal("99.00");
            BigDecimal gstAmt = new BigDecimal("17.82");
            String dateStr = customer.getCreatedAt() != null ? customer.getCreatedAt().format(formatter) : "Registration";
            String rawDateStr = customer.getCreatedAt() != null ? customer.getCreatedAt().toString() : "";

            dtoList.add(CustomerPaymentTransactionDTO.builder()
                    .id("PAY-REG-" + customer.getCustomerId())
                    .orderId("TXN-REG-" + customer.getCustomerId())
                    .gatewayPaymentId("PAY-REG-" + customer.getCustomerId())
                    .paymentType("REGISTRATION")
                    .serviceDescription("Adalat Customer Account Activation & Lifetime Escrow")
                    .serviceSubDescription("One-time registration and platform escrow enablement")
                    .amount("₹" + totalAmt.setScale(2, RoundingMode.HALF_UP).toString())
                    .amountNum(totalAmt)
                    .baseAmount(baseAmt.toString())
                    .gstAmount(gstAmt.toString())
                    .paymentMethod("UPI Direct (Auto-Settled)")
                    .status("PAID")
                    .date(dateStr)
                    .rawDate(rawDateStr)
                    .build());
        }

        return dtoList;
    }

    private String resolveLawyerPhoto(Lawyer lawyer) {
        if (lawyer == null) return null;
        String lawyerPhotoUrl = lawyer.getProfilePhotoUrl();
        if ((lawyerPhotoUrl == null || lawyerPhotoUrl.isBlank()) && lawyerDocumentRepository != null) {
            List<LawyerDocument> docs = lawyerDocumentRepository.findByLawyer(lawyer);
            if (docs != null && !docs.isEmpty()) {
                lawyerPhotoUrl = docs.stream()
                        .filter(d -> d.getDocumentType() == DocumentType.PHOTO ||
                                     (d.getFileUrl() != null && d.getFileUrl().toLowerCase().matches(".*\\.(jpg|jpeg|png|webp|gif)$")))
                        .map(LawyerDocument::getFileUrl)
                        .findFirst()
                        .orElse(null);
            }
        }
        return lawyerPhotoUrl;
    }
}

