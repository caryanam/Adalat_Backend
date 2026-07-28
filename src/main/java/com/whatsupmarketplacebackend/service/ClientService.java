package com.whatsupmarketplacebackend.service;

import com.whatsupmarketplacebackend.dto.ClientCreateRequestDTO;
import com.whatsupmarketplacebackend.dto.ClientResponseDTO;
import com.whatsupmarketplacebackend.dto.ClientUpdateRequestDTO;

import java.util.List;

public interface ClientService {

    ClientResponseDTO createClient(ClientCreateRequestDTO request);

    ClientResponseDTO getClientById(Long id);

    List<ClientResponseDTO> getAllClients();

    List<ClientResponseDTO> getAllClientsForDashboard();

    ClientResponseDTO updateClient(Long id, ClientUpdateRequestDTO request);

    void deleteClient(Long id);

    void deleteClientByCredentials(String email, String password);
}
