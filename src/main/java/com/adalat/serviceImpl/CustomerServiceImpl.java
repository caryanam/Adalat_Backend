package com.adalat.serviceImpl;

import com.adalat.dto.*;
import com.adalat.entity.Customer;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.AccountStatus;
import com.adalat.enums.PaymentStatus;
import com.adalat.enums.Role;
import com.adalat.exception.DuplicateResourceException;
import com.adalat.exception.PaymentPendingException;
import com.adalat.exception.ResourceNotFoundException;
import com.adalat.repository.CustomerRepository;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

        // Duplicate email check
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("A customer with this email already exists.");
        }

        // Duplicate mobile check
        if (customerRepository.existsByMobileNumber(request.getMobileNumber())) {
            throw new DuplicateResourceException("A customer with this mobile number already exists.");
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
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("New customer registered: id={}, email={}", saved.getCustomerId(), saved.getEmail());

        // Create and persist PaymentTransaction record in database
        try {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .customer(saved)
                    .orderId("TXN-REG-" + saved.getCustomerId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase())
                    .amount(new java.math.BigDecimal("99.00"))
                    .paymentType("REGISTRATION")
                    .status(PaymentStatus.PAID)
                    .gatewayPaymentId(request.getPaymentTransactionId() != null ? request.getPaymentTransactionId() : ("PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase()))
                    .build();
            paymentTransactionRepository.save(transaction);
            log.info("PaymentTransaction saved in DB: orderId={}, customerId={}", transaction.getOrderId(), saved.getCustomerId());
        } catch (Exception ex) {
            log.error("Failed to save PaymentTransaction record: {}", ex.getMessage());
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
                        .amount(new java.math.BigDecimal("99.00"))
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
                    .amount(new java.math.BigDecimal("99.00"))
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
}
