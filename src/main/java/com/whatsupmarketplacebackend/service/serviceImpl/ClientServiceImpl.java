package com.whatsupmarketplacebackend.service.serviceImpl;

import com.whatsupmarketplacebackend.dto.ClientCreateRequestDTO;
import com.whatsupmarketplacebackend.dto.ClientResponseDTO;
import com.whatsupmarketplacebackend.dto.ClientUpdateRequestDTO;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.enums.Role;
import com.whatsupmarketplacebackend.exception.DuplicateResourceException;
import com.whatsupmarketplacebackend.exception.ResourceNotFoundException;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final com.whatsupmarketplacebackend.repository.MessageLogRepository messageLogRepository;
    private final com.whatsupmarketplacebackend.repository.ImportLogRepository importLogRepository;
    private final com.whatsupmarketplacebackend.repository.CustomerDataRepository customerDataRepository;
    private final com.whatsupmarketplacebackend.repository.CampaignRepository campaignRepository;
    private final com.whatsupmarketplacebackend.repository.PaymentHistoryRepository paymentHistoryRepository;
    private final com.whatsupmarketplacebackend.repository.ClientSubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public ClientResponseDTO createClient(ClientCreateRequestDTO request) {

        log.info("Creating client : {}", request.email());

        validateDuplicate(request.email(), request.phoneNumber());

        if (request.category() == null) {
            throw new IllegalArgumentException("Business category is required.");
        }

        Client client = Client.builder()
                .ownerName(request.ownerName())
                .companyName(request.companyName())
                .category(request.category())
                .phoneNumber(request.phoneNumber())
                .whatsappNumber(request.whatsappNumber())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CLIENT)
                .build();

        clientRepository.save(client);

        log.info("Client created successfully with id {}", client.getId());

        return ClientResponseDTO.from(client);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponseDTO getClientById(Long id) {

        Client client = findClient(id);

        return ClientResponseDTO.from(client);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientResponseDTO> getAllClients() {

        return clientRepository.findAll()
                .stream()
                .map(ClientResponseDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientResponseDTO> getAllClientsForDashboard() {

        return clientRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ClientResponseDTO::from)
                .toList();
    }

    @Override
    public ClientResponseDTO updateClient(Long id,
                                          ClientUpdateRequestDTO request) {

        Client client = findClient(id);

        validateDuplicateForUpdate(
                request.email(),
                request.phoneNumber(),
                id
        );

        if (request.category() == null) {
            throw new IllegalArgumentException("Business category is required.");
        }

        client.setOwnerName(request.ownerName());
        client.setCompanyName(request.companyName());
        client.setCategory(request.category());
        client.setPhoneNumber(request.phoneNumber());
        client.setWhatsappNumber(request.whatsappNumber());
        client.setEmail(request.email());

        clientRepository.save(client);

        log.info("Client updated : {}", id);

        return ClientResponseDTO.from(client);
    }

    @Override
    public void deleteClient(Long id) {

        Client client = findClient(id);
        deleteClientAssociatedData(id);
        clientRepository.delete(client);

        log.info("Client deleted : {}", id);
    }

    @Override
    public void deleteClientByCredentials(String email, String password) {

        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with email: " + email
                ));

        if (!passwordEncoder.matches(password, client.getPassword())) {
            throw new IllegalArgumentException("Invalid password. Account deletion denied.");
        }

        deleteClientAssociatedData(client.getId());
        clientRepository.delete(client);

        log.info("Client account deleted for email: {}", email);
    }

    private void deleteClientAssociatedData(Long clientId) {
        messageLogRepository.deleteByClientId(clientId);
        importLogRepository.deleteByClientId(clientId);
        customerDataRepository.deleteByClientId(clientId);
        paymentHistoryRepository.deleteByClientSubscription_Client_Id(clientId);
        campaignRepository.deleteByClientId(clientId);
        subscriptionRepository.deleteByClientId(clientId);
    }

    private Client findClient(Long id) {

        return clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id : " + id
                        ));
    }


    private void validateDuplicate(String email,
                                   String phoneNumber) {

        if (clientRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "Email already registered."
            );
        }

        if (clientRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DuplicateResourceException(
                    "Phone number already registered."
            );
        }
    }

    private void validateDuplicateForUpdate(String email,
                                            String phoneNumber,
                                            Long id) {

        if (clientRepository.existsByEmailAndIdNot(email, id)) {

            throw new DuplicateResourceException(
                    "Email already registered."
            );
        }

        if (clientRepository.existsByPhoneNumberAndIdNot(phoneNumber, id)) {

            throw new DuplicateResourceException(
                    "Phone number already registered."
            );
        }
    }

}
