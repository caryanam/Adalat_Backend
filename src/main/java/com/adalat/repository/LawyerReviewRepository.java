package com.adalat.repository;

import com.adalat.entity.LawyerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawyerReviewRepository extends JpaRepository<LawyerReview, Long> {
    List<LawyerReview> findByLawyer_LawyerIdOrderByCreatedAtDesc(Long lawyerId);
    Optional<LawyerReview> findByConsultationRequestId(Long consultationRequestId);
}
