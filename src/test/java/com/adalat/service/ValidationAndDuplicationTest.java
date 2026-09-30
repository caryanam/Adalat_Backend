package com.adalat.service;

import com.adalat.dto.*;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.enums.Role;
import com.adalat.exception.DuplicateResourceException;
import com.adalat.repository.AdminRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.EmailOtpRepository;
import com.adalat.repository.LawyerRepository;
import com.adalat.serviceImpl.CustomerServiceImpl;
import com.adalat.serviceImpl.EmailOtpServiceImpl;
import com.adalat.serviceImpl.LawyerServiceImpl;
import com.adalat.util.ValidationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ValidationAndDuplicationTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LawyerRepository lawyerRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private EmailOtpRepository emailOtpRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.adalat.security.JwtService jwtService;

    @Mock
    private EmailOtpService emailOtpService;

    @Mock
    private com.adalat.service.NotificationService notificationService;

    @Mock
    private com.adalat.repository.PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private com.adalat.repository.LawyerDocumentRepository lawyerDocumentRepository;

    @Mock
    private com.adalat.repository.ConsultationRequestRepository consultationRequestRepository;

    @Mock
    private com.adalat.service.FileStorageService fileStorageService;

    @InjectMocks
    private EmailOtpServiceImpl emailOtpServiceImpl;

    private CustomerServiceImpl customerService;
    private LawyerServiceImpl lawyerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(
                customerRepository,
                lawyerRepository,
                adminRepository,
                paymentTransactionRepository,
                consultationRequestRepository,
                lawyerDocumentRepository,
                passwordEncoder,
                jwtService,
                emailOtpService,
                emailService,
                notificationService
        );

        lawyerService = new LawyerServiceImpl(
                lawyerRepository,
                lawyerDocumentRepository,
                customerRepository,
                adminRepository,
                emailOtpRepository,
                paymentTransactionRepository,
                consultationRequestRepository,
                passwordEncoder,
                jwtService,
                emailOtpService,
                emailService,
                fileStorageService,
                notificationService
        );
    }

    @Test
    @DisplayName("ValidationUtils: Correctly normalizes and validates mobile numbers")
    void testMobileValidationUtils() {
        assertEquals("9876543210", ValidationUtils.normalizeMobile("9876543210"));
        assertEquals("9876543210", ValidationUtils.normalizeMobile("+91 98765 43210"));
        assertEquals("9876543210", ValidationUtils.normalizeMobile("919876543210"));
        assertEquals("9876543210", ValidationUtils.normalizeMobile("09876543210"));
        assertEquals("9876543210", ValidationUtils.normalizeMobile("98765-43210"));

        assertTrue(ValidationUtils.isValidMobile("9876543210"));
        assertTrue(ValidationUtils.isValidMobile("+91 9876543210"));
        assertTrue(ValidationUtils.isValidMobile("6123456789"));
        assertTrue(ValidationUtils.isValidMobile("7123456789"));
        assertTrue(ValidationUtils.isValidMobile("8123456789"));

        assertFalse(ValidationUtils.isValidMobile("5123456789")); // Starts with 5
        assertFalse(ValidationUtils.isValidMobile("12345")); // Short
        assertFalse(ValidationUtils.isValidMobile("abcdefghij")); // Non-numeric
    }

    @Test
    @DisplayName("ValidationUtils: Correctly normalizes and validates email addresses")
    void testEmailValidationUtils() {
        assertEquals("test@gmail.com", ValidationUtils.normalizeEmail("  Test@Gmail.com  "));
        assertTrue(ValidationUtils.isValidEmail("test@example.com"));
        assertFalse(ValidationUtils.isValidEmail("invalid-email"));
        assertFalse(ValidationUtils.isValidEmail("test@"));
    }

    @Test
    @DisplayName("EmailOtpService: Rejects duplicate email on sendRegistrationOtp")
    void testSendRegistrationOtp_DuplicateEmail() {
        when(customerRepository.existsByEmail("existing@customer.com")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                emailOtpServiceImpl.sendRegistrationOtp("existing@customer.com", Role.CUSTOMER, "Customer"));

        assertTrue(ex.getMessage().contains("already registered to a Customer account"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("EmailOtpService: Rejects duplicate advocate email on sendRegistrationOtp")
    void testSendRegistrationOtp_DuplicateAdvocateEmail() {
        when(customerRepository.existsByEmail("advocate@legal.com")).thenReturn(false);
        when(lawyerRepository.existsByEmail("advocate@legal.com")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                emailOtpServiceImpl.sendRegistrationOtp("advocate@legal.com", Role.CUSTOMER, "User"));

        assertTrue(ex.getMessage().contains("already registered to an Advocate account"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("EmailOtpService: checkEmailAvailability returns correct status")
    void testCheckEmailAvailability() {
        when(customerRepository.existsByEmail("customer@test.com")).thenReturn(true);
        Map<String, Object> res1 = emailOtpServiceImpl.checkEmailAvailability("customer@test.com");
        assertTrue((Boolean) res1.get("exists"));
        assertEquals("CUSTOMER", res1.get("role"));

        when(customerRepository.existsByEmail("free@test.com")).thenReturn(false);
        when(lawyerRepository.existsByEmail("free@test.com")).thenReturn(false);
        when(adminRepository.existsByEmail("free@test.com")).thenReturn(false);
        Map<String, Object> res2 = emailOtpServiceImpl.checkEmailAvailability("free@test.com");
        assertFalse((Boolean) res2.get("exists"));
    }

    @Test
    @DisplayName("EmailOtpService: checkMobileAvailability returns correct status")
    void testCheckMobileAvailability() {
        when(customerRepository.existsByMobileNumber("9876543210")).thenReturn(true);
        Map<String, Object> res1 = emailOtpServiceImpl.checkMobileAvailability("+91 98765 43210");
        assertTrue((Boolean) res1.get("exists"));
        assertEquals("CUSTOMER", res1.get("role"));

        when(customerRepository.existsByMobileNumber("9999988888")).thenReturn(false);
        when(lawyerRepository.existsByMobileNumber("9999988888")).thenReturn(false);
        Map<String, Object> res2 = emailOtpServiceImpl.checkMobileAvailability("9999988888");
        assertFalse((Boolean) res2.get("exists"));
    }

    @Test
    @DisplayName("CustomerService: Rejects registration when mobile number is already registered")
    void testCustomerRegistration_DuplicateMobile() {
        CustomerRegistrationRequestDTO request = CustomerRegistrationRequestDTO.builder()
                .fullName("John Doe")
                .email("john@new.com")
                .mobileNumber("+91 9876543210")
                .password("Password123")
                .confirmPassword("Password123")
                .termsAccepted(true)
                .privacyPolicyAccepted(true)
                .build();

        when(customerRepository.existsByEmail("john@new.com")).thenReturn(false);
        when(lawyerRepository.existsByEmail("john@new.com")).thenReturn(false);
        when(adminRepository.existsByEmail("john@new.com")).thenReturn(false);
        when(customerRepository.existsByMobileNumber("9876543210")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                customerService.registerCustomer(request));

        assertTrue(ex.getMessage().contains("mobile number is already registered"));
    }

    @Test
    @DisplayName("LawyerService: Rejects registration when mobile number is already registered")
    void testLawyerRegistration_DuplicateMobile() {
        LawyerAccountRequestDTO request = LawyerAccountRequestDTO.builder()
                .fullName("Adv. John Doe")
                .email("advjohn@new.com")
                .mobileNumber("9876543210")
                .password("Password123")
                .build();

        when(lawyerRepository.existsByEmail("advjohn@new.com")).thenReturn(false);
        when(customerRepository.existsByEmail("advjohn@new.com")).thenReturn(false);
        when(adminRepository.existsByEmail("advjohn@new.com")).thenReturn(false);
        when(lawyerRepository.existsByMobileNumber("9876543210")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                lawyerService.registerStep1(request));

        assertTrue(ex.getMessage().contains("mobile number is already registered"));
    }
}
