package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.WhatsAppTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, Long> {

    Optional<WhatsAppTemplate> findByMetaTemplateId(String metaTemplateId);

    List<WhatsAppTemplate> findByStatus(String status);

    List<WhatsAppTemplate> findAllByOrderByCreatedAtDesc();

    boolean existsByMetaTemplateId(String metaTemplateId);
}
