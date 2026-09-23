package com.adalat.serviceImpl;

import com.adalat.dto.DeleteAccountRequestDTO;
import com.adalat.dto.DeleteAccountResponseDTO;
import com.adalat.entity.*;
import com.adalat.enums.Role;
import com.adalat.repository.*;
import com.adalat.security.CustomUserDetails;
import com.adalat.service.AccountService;
import com.adalat.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;
    private final AdminRepository adminRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final ConsultationChatMessageRepository consultationChatMessageRepository;
    private final LegalIntakeSessionRepository legalIntakeSessionRepository;
    private final LegalIntakeMessageRepository legalIntakeMessageRepository;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final LawyerReviewRepository lawyerReviewRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public DeleteAccountResponseDTO deleteAccount(CustomUserDetails userDetails, DeleteAccountRequestDTO request) {
        if (userDetails == null) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Authentication required to delete account.")
                    .build();
        }

        String inputIdentifier = request.getEmail() != null ? request.getEmail().trim() : "";
        String inputPassword = request.getPassword() != null ? request.getPassword() : "";

        if (inputIdentifier.isBlank() || inputPassword.isBlank()) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        Role role = userDetails.getRole();
        Long userId = userDetails.getId();

        if (Role.CUSTOMER.equals(role)) {
            return deleteCustomerAccount(userId, inputIdentifier, inputPassword);
        } else if (Role.LAWYER.equals(role)) {
            return deleteLawyerAccount(userId, inputIdentifier, inputPassword);
        } else if (Role.ADMIN.equals(role)) {
            return deleteAdminAccount(userId, inputIdentifier, inputPassword);
        }

        return DeleteAccountResponseDTO.builder()
                .success(false)
                .message("Unsupported account role.")
                .build();
    }

    private DeleteAccountResponseDTO deleteCustomerAccount(Long customerId, String inputIdentifier, String inputPassword) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Customer account not found.")
                    .build();
        }

        // Verify identifier (email or mobile)
        boolean identifierMatch = inputIdentifier.equalsIgnoreCase(customer.getEmail())
                || inputIdentifier.equals(customer.getMobileNumber());
        if (!identifierMatch) {
            log.warn("Account deletion failed: Identifier mismatch for customerId={}", customerId);
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        // Verify password
        if (!passwordEncoder.matches(inputPassword, customer.getPassword())) {
            log.warn("Account deletion failed: Password mismatch for customerId={}", customerId);
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        log.info("Starting cascade deletion for customerId={}, email={}", customerId, customer.getEmail());

        // 1. Delete all consultations & chat history
        List<ConsultationRequest> requests = consultationRequestRepository.findByCustomer(customer);
        for (ConsultationRequest cr : requests) {
            consultationChatMessageRepository.deleteByConsultationRequest(cr);
            fileStorageService.deleteConsultationDirectory(cr.getId());
            paymentTransactionRepository.deleteByConsultationRequest(cr);
            lawyerReviewRepository.deleteByConsultationRequestId(cr.getId());
            consultationRequestRepository.delete(cr);
        }

        // 2. Delete all legal intake sessions & messages
        List<LegalIntakeSession> sessions = legalIntakeSessionRepository.findByCustomer(customer);
        for (LegalIntakeSession session : sessions) {
            legalIntakeMessageRepository.deleteBySessionId(session.getId());
            legalIntakeSessionRepository.delete(session);
        }

        // 3. Purge uploaded customer files from disk
        fileStorageService.deleteCustomerDirectory(customerId);

        // 4. Delete payment transactions & reviews
        paymentTransactionRepository.deleteByCustomer(customer);
        lawyerReviewRepository.deleteByCustomer(customer);

        // 5. Delete notifications
        notificationRepository.deleteByRecipientRoleAndRecipientId(Role.CUSTOMER, customerId);

        // 6. Delete email OTPs
        emailOtpRepository.deleteByEmailAndRole(customer.getEmail(), Role.CUSTOMER);

        // 7. Delete Customer entity
        customerRepository.delete(customer);

        log.info("Customer account successfully deleted: customerId={}", customerId);

        return DeleteAccountResponseDTO.builder()
                .success(true)
                .message("Your account and associated data have been permanently deleted.")
                .build();
    }

    private DeleteAccountResponseDTO deleteLawyerAccount(Long lawyerId, String inputIdentifier, String inputPassword) {
        Lawyer lawyer = lawyerRepository.findById(lawyerId).orElse(null);
        if (lawyer == null) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Lawyer account not found.")
                    .build();
        }

        // Verify identifier (email or mobile)
        boolean identifierMatch = inputIdentifier.equalsIgnoreCase(lawyer.getEmail())
                || inputIdentifier.equals(lawyer.getMobileNumber());
        if (!identifierMatch) {
            log.warn("Account deletion failed: Identifier mismatch for lawyerId={}", lawyerId);
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        // Verify password
        if (!passwordEncoder.matches(inputPassword, lawyer.getPassword())) {
            log.warn("Account deletion failed: Password mismatch for lawyerId={}", lawyerId);
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        log.info("Starting cascade deletion for lawyerId={}, email={}", lawyerId, lawyer.getEmail());

        // 1. Delete all consultations & chat history
        List<ConsultationRequest> requests = consultationRequestRepository.findByLawyer(lawyer);
        for (ConsultationRequest cr : requests) {
            consultationChatMessageRepository.deleteByConsultationRequest(cr);
            fileStorageService.deleteConsultationDirectory(cr.getId());
            paymentTransactionRepository.deleteByConsultationRequest(cr);
            lawyerReviewRepository.deleteByConsultationRequestId(cr.getId());
            consultationRequestRepository.delete(cr);
        }

        // 2. Delete lawyer documents and clear files on disk
        lawyerDocumentRepository.deleteByLawyer(lawyer);
        fileStorageService.deleteLawyerDirectory(lawyerId);

        // 3. Delete reviews
        lawyerReviewRepository.deleteByLawyer(lawyer);

        // 4. Delete notifications
        notificationRepository.deleteByRecipientRoleAndRecipientId(Role.LAWYER, lawyerId);

        // 5. Delete email OTPs
        emailOtpRepository.deleteByEmailAndRole(lawyer.getEmail(), Role.LAWYER);

        // 6. Clear collection mappings
        if (lawyer.getPracticeAreas() != null) {
            lawyer.getPracticeAreas().clear();
        }
        if (lawyer.getLanguages() != null) {
            lawyer.getLanguages().clear();
        }

        // 7. Delete Lawyer entity
        lawyerRepository.delete(lawyer);

        log.info("Lawyer account successfully deleted: lawyerId={}", lawyerId);

        return DeleteAccountResponseDTO.builder()
                .success(true)
                .message("Your account and associated data have been permanently deleted.")
                .build();
    }

    private DeleteAccountResponseDTO deleteAdminAccount(Long adminId, String inputIdentifier, String inputPassword) {
        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Admin account not found.")
                    .build();
        }

        boolean identifierMatch = inputIdentifier.equalsIgnoreCase(admin.getEmail())
                || inputIdentifier.equals(admin.getMobileNumber());
        if (!identifierMatch || !passwordEncoder.matches(inputPassword, admin.getPassword())) {
            return DeleteAccountResponseDTO.builder()
                    .success(false)
                    .message("Invalid email or password.")
                    .build();
        }

        notificationRepository.deleteByRecipientRoleAndRecipientId(Role.ADMIN, adminId);
        emailOtpRepository.deleteByEmail(admin.getEmail());
        adminRepository.delete(admin);

        return DeleteAccountResponseDTO.builder()
                .success(true)
                .message("Your account and associated data have been permanently deleted.")
                .build();
    }
}
