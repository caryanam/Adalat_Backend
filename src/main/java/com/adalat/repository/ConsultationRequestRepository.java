package com.adalat.repository;

import com.adalat.entity.Customer;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Lawyer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.enums.ConsultationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Long> {

    List<ConsultationRequest> findByCustomerOrderByCreatedAtDesc(Customer customer);

    List<ConsultationRequest> findByLawyerOrderByCreatedAtDesc(Lawyer lawyer);

    List<ConsultationRequest> findByLawyerAndStatusOrderByCreatedAtDesc(Lawyer lawyer, ConsultationRequestStatus status);

    List<ConsultationRequest> findByLegalSession(LegalAssistanceSession legalSession);

    Optional<ConsultationRequest> findByLegalSessionAndLawyer(LegalAssistanceSession legalSession, Lawyer lawyer);
}
