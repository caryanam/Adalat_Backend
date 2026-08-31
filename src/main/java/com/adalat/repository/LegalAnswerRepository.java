package com.adalat.repository;

import com.adalat.entity.LegalAnswer;
import com.adalat.entity.LegalAssistanceSession;
import com.adalat.entity.LegalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalAnswerRepository extends JpaRepository<LegalAnswer, Long> {

    List<LegalAnswer> findBySessionOrderByCreatedAtAsc(LegalAssistanceSession session);

    Optional<LegalAnswer> findBySessionAndQuestion(LegalAssistanceSession session, LegalQuestion question);
}
