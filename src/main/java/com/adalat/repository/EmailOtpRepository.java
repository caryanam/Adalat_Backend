package com.adalat.repository;

import com.adalat.entity.EmailOtp;
import com.adalat.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findTopByEmailAndRoleOrderByCreatedAtDesc(String email, Role role);

    Optional<EmailOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailOtp> findTopByEmailAndUsedTrueOrderByVerifiedAtDesc(String email);

    void deleteByEmailAndRole(String email, Role role);

    void deleteByEmail(String email);
}
