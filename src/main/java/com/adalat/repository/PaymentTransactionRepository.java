package com.adalat.repository;

import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Customer;
import com.adalat.entity.PaymentTransaction;
import com.adalat.entity.Lawyer;
import com.adalat.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT p FROM PaymentTransaction p WHERE p.consultationRequest.lawyer = :lawyer ORDER BY p.createdAt DESC")
    List<PaymentTransaction> findByConsultationRequest_LawyerOrderByCreatedAtDesc(@Param("lawyer") Lawyer lawyer);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.consultationRequest.lawyer.lawyerId = :lawyerId ORDER BY p.createdAt DESC")
    List<PaymentTransaction> findByLawyerId(@Param("lawyerId") Long lawyerId);
}
