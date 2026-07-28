package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.PaymentHistory;
import com.whatsupmarketplacebackend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    List<PaymentHistory> findByClientSubscription_ClientIdOrderByCreatedAtDesc(Long clientId);
    List<PaymentHistory> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    void deleteByClientSubscription_Client_Id(Long clientId);
}
