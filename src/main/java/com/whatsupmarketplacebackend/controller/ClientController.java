package com.whatsupmarketplacebackend.controller;


import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.ClientCreateRequestDTO;
import com.whatsupmarketplacebackend.dto.ClientResponseDTO;
import com.whatsupmarketplacebackend.dto.ClientUpdateRequestDTO;
import com.whatsupmarketplacebackend.dto.ClientDeleteRequestDTO;
import com.whatsupmarketplacebackend.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Endpoints for Client registration, profile management, and account operations")
public class ClientController {

    private final ClientService clientService;

    //Client Registration
    @PostMapping("/registration")
    @Operation(summary = "Register a new client", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or duplicate email/phone")
    })
    public ResponseEntity<ApiResponseDTO<ClientResponseDTO>> createClient(
            @Valid @RequestBody ClientCreateRequestDTO request) {

        ClientResponseDTO response = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>(
                        "SUCCESS",
                        "Client created successfully.",
                        response
                ));
    }


    //Get Client By Id
    @GetMapping("/{id}")
    @Operation(summary = "Get client details by ID", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Client not found")
    })
    public ResponseEntity<ApiResponseDTO<ClientResponseDTO>> getClientById(
            @PathVariable Long id) {

        ClientResponseDTO response = clientService.getClientById(id);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Client fetched successfully.",
                        response
                )
        );
    }


    //Get All Clients
    @GetMapping("/all")
    @Operation(summary = "Get all registered clients", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clients fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required")
    })
    public ResponseEntity<ApiResponseDTO<List<ClientResponseDTO>>> getAllClients() {

        List<ClientResponseDTO> response = clientService.getAllClients();

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Clients fetched successfully.",
                        response
                )
        );
    }


     //Update Client Info
    @PutMapping("/update/{id}")
    @Operation(summary = "Update client profile details", description = "Access Level: Protected [Required Roles: CLIENT, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required"),
            @ApiResponse(responseCode = "404", description = "Client not found")
    })
    public ResponseEntity<ApiResponseDTO<ClientResponseDTO>> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody ClientUpdateRequestDTO request) {

        ClientResponseDTO response =
                clientService.updateClient(id, request);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Client updated successfully.",
                        response
                )
        );
    }


     //Delete Client Account (Public - requires email & password verification)
    @PostMapping("/delete-account")
    @Operation(summary = "Delete client account via email & password", description = "Access Level: Public (Self-delete via Email & Password credentials verification)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client account deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid credentials or password mismatch"),
            @ApiResponse(responseCode = "404", description = "Client not found with specified email")
    })
    public ResponseEntity<ApiResponseDTO<Object>> deleteClientAccount(
            @Valid @RequestBody ClientDeleteRequestDTO request) {

        clientService.deleteClientByCredentials(request.email(), request.password());

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Client account deleted successfully.",
                        null
                )
        );
    }

}
