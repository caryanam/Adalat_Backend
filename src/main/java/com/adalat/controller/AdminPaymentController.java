package com.adalat.controller;

import com.adalat.dto.ApiResponseDTO;
import com.adalat.entity.PaymentTransaction;
import com.adalat.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<Map<String, Object>>>> getAllPayments() {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

        for (PaymentTransaction tx : transactions) {
            Map<String, Object> map = new HashMap<>();
            String txId = tx.getOrderId() != null ? tx.getOrderId() : ("TXN-DB-" + tx.getId());
            map.put("id", txId);

            String cName = tx.getCustomer() != null ? tx.getCustomer().getFullName() : "Registered Customer";
            String cEmail = tx.getCustomer() != null ? tx.getCustomer().getEmail() : "";
            map.put("customer", cName);
            map.put("customerEmail", cEmail);

            boolean isReg = tx.getPaymentType() == null || tx.getPaymentType().equalsIgnoreCase("REGISTRATION");
            map.put("lawyer", isReg ? "ADALAT Platform Pool" : "Advocate Account");
            map.put("payoutReceiver", isReg ? "ADALAT Platform Pool" : "Advocate Account");
            map.put("type", isReg ? "CUSTOMER_REGISTRATION" : "LAWYER_CONSULTATION");
            map.put("typeLabel", isReg ? "Customer Registration Fee" : "Advocate Consultation Fee");
            map.put("amount", "₹" + (tx.getAmount() != null ? tx.getAmount().toString() : "99.00"));
            map.put("amountNum", tx.getAmount() != null ? tx.getAmount().doubleValue() : 99.0);
            map.put("date", tx.getCreatedAt() != null ? tx.getCreatedAt().format(formatter) : "Today");
            map.put("status", tx.getStatus() != null ? tx.getStatus().name() : "PAID");
            map.put("method", "UPI (Instant)");
            result.add(map);
        }

        return ResponseEntity.ok(new ApiResponseDTO<>("SUCCESS", "Payment transactions fetched from MySQL database", result));
    }
}
