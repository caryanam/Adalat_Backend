package com.adalat.repository;

import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.PaymentTransaction;
import com.adalat.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByOrderId(String orderId);

    List<PaymentTransaction> findByCustomer(Customer customer);

    Optional<PaymentTransaction> findByCustomerAndStatus(Customer customer, PaymentStatus status);

    Optional<PaymentTransaction> findByConsultationRequestAndStatus(ConsultationRequest request, PaymentStatus status);

    List<PaymentTransaction> findByConsultationRequest(ConsultationRequest request);
}
