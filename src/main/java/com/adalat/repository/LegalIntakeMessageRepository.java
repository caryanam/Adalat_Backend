package com.adalat.repository;

import com.adalat.entity.LegalIntakeMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalIntakeMessageRepository extends JpaRepository<LegalIntakeMessage, Long> {
    
    // Find all messages for a specific session ordered by creation time
    List<LegalIntakeMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    // Idempotency: find if a message with clientMessageId already exists in this session
    java.util.Optional<LegalIntakeMessage> findFirstBySessionIdAndClientMessageId(Long sessionId, String clientMessageId);

    // Find the latest message in this session
    java.util.Optional<LegalIntakeMessage> findFirstBySessionIdOrderByIdDesc(Long sessionId);
}
