package com.adalat.service;

import com.adalat.dto.*;

public interface CustomerService {

    CustomerRegistrationResponseDTO registerCustomer(CustomerRegistrationRequestDTO request);

    PaymentInitiateResponseDTO initiatePayment(PaymentInitiateRequestDTO request);

    ApiResponseDTO<Void> verifyPayment(PaymentVerifyRequestDTO request);

    CustomerLoginResponseDTO loginCustomer(LoginRequestDTO request);
}
