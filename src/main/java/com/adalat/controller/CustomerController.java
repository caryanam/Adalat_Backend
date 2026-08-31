package com.adalat.controller;

import com.adalat.dto.*;
import com.adalat.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // ─── POST /api/customer/register ─────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<CustomerRegistrationResponseDTO>> register(
            @Valid @RequestBody CustomerRegistrationRequestDTO request) {

        CustomerRegistrationResponseDTO response = customerService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>("SUCCESS",
                        "Registration successful. Please complete the ₹99 payment to activate your account.",
                        response));
    }

    // ─── POST /api/customer/payment/initiate ─────────────────────────────────
    @PostMapping("/payment/initiate")
    public ResponseEntity<ApiResponseDTO<PaymentInitiateResponseDTO>> initiatePayment(
            @Valid @RequestBody PaymentInitiateRequestDTO request) {

        PaymentInitiateResponseDTO response = customerService.initiatePayment(request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS",
                "Payment order created. Amount: ₹99.", response));
    }

    // ─── POST /api/customer/payment/verify ───────────────────────────────────
    @PostMapping("/payment/verify")
    public ResponseEntity<ApiResponseDTO<Void>> verifyPayment(
            @Valid @RequestBody PaymentVerifyRequestDTO request) {

        ApiResponseDTO<Void> response = customerService.verifyPayment(request);
        return ResponseEntity.ok(response);
    }

    // ─── POST /api/customer/login ─────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<CustomerLoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {

        CustomerLoginResponseDTO response = customerService.loginCustomer(request);
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Login successful", response));
    }
}
