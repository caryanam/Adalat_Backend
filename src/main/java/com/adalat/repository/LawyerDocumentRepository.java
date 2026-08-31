package com.adalat.repository;

import com.adalat.entity.Lawyer;
import com.adalat.entity.LawyerDocument;
import com.adalat.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawyerDocumentRepository extends JpaRepository<LawyerDocument, Long> {

    List<LawyerDocument> findByLawyer(Lawyer lawyer);

    Optional<LawyerDocument> findByLawyerAndDocumentType(Lawyer lawyer, DocumentType documentType);
}
