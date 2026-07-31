package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    List<WebhookLog> findByWhatsappMessageId(String whatsappMessageId);
}
