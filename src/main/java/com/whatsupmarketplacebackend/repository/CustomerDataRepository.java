package com.whatsupmarketplacebackend.repository;

import com.whatsupmarketplacebackend.entity.CustomerData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

@Repository
public interface CustomerDataRepository extends JpaRepository<CustomerData, Long> {
    List<CustomerData> findByClientId(Long clientId);

    long countByClientId(Long clientId);

    Optional<CustomerData> findByClientIdAndWhatsappNumber(Long clientId, String whatsappNumber);

    @Query(
        "SELECT c FROM CustomerData c WHERE c.client.id = :clientId " +
        "AND c.id NOT IN (SELECT m.customer.id FROM MessageLog m WHERE m.client.id = :clientId)"
    )
    List<CustomerData> findUncontactedCustomers(@Param("clientId") Long clientId, Pageable pageable);

    void deleteByClientId(Long clientId);
}
