package com.adalat.repository;

import com.adalat.entity.LegalAssistanceSession;
import com.adalat.entity.LegalChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalChatMessageRepository extends JpaRepository<LegalChatMessage, Long> {

    List<LegalChatMessage> findBySessionOrderByCreatedAtAsc(LegalAssistanceSession session);
}
