package com.whatsupmarketplacebackend.security;

import com.whatsupmarketplacebackend.entity.Admin;
import com.whatsupmarketplacebackend.entity.Client;
import com.whatsupmarketplacebackend.enums.Role;
import com.whatsupmarketplacebackend.repository.AdminRepository;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final ClientRepository clientRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Admin> admin = adminRepository.findByEmail(email);
        if (admin.isPresent()) {
            Admin a = admin.get();
            return new CustomUserDetails(a.getAdminId() , a.getEmail(), a.getPassword(), Role.ADMIN);
        }

        Optional<Client> client = clientRepository.findByEmail(email);
        if (client.isPresent()) {
            Client c = client.get();
            Role clientRole = (c.getRole() != null) ? c.getRole() : Role.CLIENT;
            return new CustomUserDetails(c.getId(), c.getEmail(), c.getPassword(), clientRole);
        }

        throw new UsernameNotFoundException("User not found");
    }
}
