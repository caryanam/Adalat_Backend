package com.adalat.repository;

import com.adalat.entity.LegalIntakeSession;
import com.adalat.enums.IntakeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalIntakeSessionRepository extends JpaRepository<LegalIntakeSession, Long> {
    
    // Find the latest active or summary_ready session for a customer
    Optional<LegalIntakeSession> findFirstByCustomerCustomerIdAndStatusInOrderByIdDesc(Long customerId, List<IntakeStatus> statuses);
    
    // Find all sessions for a customer
    List<LegalIntakeSession> findByCustomer(com.adalat.entity.Customer customer);

    List<LegalIntakeSession> findByCustomerCustomerIdOrderByIdDesc(Long customerId);
}
