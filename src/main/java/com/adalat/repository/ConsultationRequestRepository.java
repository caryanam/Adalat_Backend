package com.adalat.repository;

import com.adalat.entity.Customer;
import com.adalat.entity.ConsultationRequest;
import com.adalat.entity.Lawyer;
import com.adalat.enums.ConsultationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Long> {

    List<ConsultationRequest> findByCustomer(Customer customer);

    List<ConsultationRequest> findByCustomerOrderByCreatedAtDesc(Customer customer);

    List<ConsultationRequest> findByCustomerAndStatusInOrderByCreatedAtDesc(Customer customer, List<ConsultationRequestStatus> statuses);

    List<ConsultationRequest> findByLawyer(Lawyer lawyer);

    List<ConsultationRequest> findByLawyerOrderByCreatedAtDesc(Lawyer lawyer);

    List<ConsultationRequest> findAllByOrderByCreatedAtDesc();

    List<ConsultationRequest> findByLawyerAndStatusOrderByCreatedAtDesc(Lawyer lawyer, ConsultationRequestStatus status);

    List<ConsultationRequest> findByLawyerAndStatusInOrderByCreatedAtDesc(Lawyer lawyer, List<ConsultationRequestStatus> statuses);

    Optional<ConsultationRequest> findByIdAndCustomer(Long id, Customer customer);

    Optional<ConsultationRequest> findByIdAndLawyer(Long id, Lawyer lawyer);
}
