package com.adalat.repository;

import com.adalat.entity.Customer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.LegalSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalAssistanceSessionRepository extends JpaRepository<LegalAssistanceSession, Long> {

    List<LegalAssistanceSession> findByCustomerOrderByCreatedAtDesc(Customer customer);

    Optional<LegalAssistanceSession> findByIdAndCustomer(Long id, Customer customer);

    List<LegalAssistanceSession> findByStatusOrderByCreatedAtDesc(LegalSessionStatus status);

    List<LegalAssistanceSession> findAllByOrderByCreatedAtDesc();
}
