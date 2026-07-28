package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.ImportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportLogRepository extends JpaRepository<ImportLog, Long> {
    List<ImportLog> findByClientIdOrderByImportedAtDesc(Long clientId);
    java.util.Optional<ImportLog> findFirstByClientIdOrderByImportedAtDesc(Long clientId);

    void deleteByClientId(Long clientId);
}
