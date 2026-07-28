package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.Feedback;
import com.whatsupmarketplacebackend.enums.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<Feedback> findByClientId(Long clientId);
}
