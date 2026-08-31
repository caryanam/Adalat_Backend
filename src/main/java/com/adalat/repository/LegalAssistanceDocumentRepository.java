package com.adalat.repository;

import com.adalat.entity.Customer;
import com.adalat.entity.LegalAssistanceDocument;
import com.adalat.entity.LegalAssistanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalAssistanceDocumentRepository extends JpaRepository<LegalAssistanceDocument, Long> {

    List<LegalAssistanceDocument> findBySession(LegalAssistanceSession session);

    List<LegalAssistanceDocument> findByCustomer(Customer customer);
}
