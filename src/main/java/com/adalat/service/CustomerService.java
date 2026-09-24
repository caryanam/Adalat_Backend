package com.adalat.service;

import com.adalat.dto.*;

public interface CustomerService {

    CustomerRegistrationResponseDTO registerCustomer(CustomerRegistrationRequestDTO request);

    PaymentInitiateResponseDTO initiatePayment(PaymentInitiateRequestDTO request);

    ApiResponseDTO<Void> verifyPayment(PaymentVerifyRequestDTO request);

    CustomerLoginResponseDTO loginCustomer(LoginRequestDTO request);

    CustomerInfoDTO updateProfile(Long customerId, CustomerUpdateProfileRequestDTO request);

    void changePassword(Long customerId, ChangePasswordRequestDTO request);

    void resetPasswordWithEmailOtp(String email, String newPassword, String confirmPassword);

    java.util.List<CustomerPaymentTransactionDTO> getCustomerPaymentHistory(Long customerId);
}

