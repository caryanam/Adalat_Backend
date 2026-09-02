package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.entity.Customer;
import com.adalat.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerRepository customerRepository;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<Customer>>> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Registered customers fetched successfully", customers));
    }
}
