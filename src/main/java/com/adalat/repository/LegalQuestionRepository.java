package com.adalat.repository;

import com.adalat.entity.LegalAssistanceSession;
import com.adalat.entity.LegalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalQuestionRepository extends JpaRepository<LegalQuestion, Long> {

    List<LegalQuestion> findBySessionOrderByQuestionNumberAsc(LegalAssistanceSession session);

    Optional<LegalQuestion> findBySessionAndQuestionNumber(LegalAssistanceSession session, Integer questionNumber);
}
