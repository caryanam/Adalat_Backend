package com.adalat.repository;

import com.adalat.entity.Lawyer;
import com.adalat.enums.RegistrationStatus;
import com.adalat.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawyerRepository extends JpaRepository<Lawyer, Long> {

    Optional<Lawyer> findByEmail(String email);

    Optional<Lawyer> findByMobileNumber(String mobileNumber);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);

    List<Lawyer> findByVerificationStatus(VerificationStatus verificationStatus);

    List<Lawyer> findByRegistrationStatusAndVerificationStatus(
            RegistrationStatus registrationStatus,
            VerificationStatus verificationStatus);
}
