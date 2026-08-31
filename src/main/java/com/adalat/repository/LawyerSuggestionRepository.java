package com.adalat.repository;

import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerSuggestion;
import com.adalat.entity.LegalAssistanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawyerSuggestionRepository extends JpaRepository<LawyerSuggestion, Long> {

    List<LawyerSuggestion> findByLegalSession(LegalAssistanceSession legalSession);

    Optional<LawyerSuggestion> findByLegalSessionAndLawyer(LegalAssistanceSession legalSession, Lawyer lawyer);
}
