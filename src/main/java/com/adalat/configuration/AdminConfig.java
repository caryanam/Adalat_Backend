package com.adalat.configuration;

import com.adalat.entity.Admin;
import com.adalat.enums.Role;
import com.adalat.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminConfig implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // CHECK ADMIN EXISTS BY EMAIL
        if (adminRepository.findByEmail("admin@gmail.com").isEmpty()) {

            Admin admin = new Admin();

            admin.setFullName("Admin");
            admin.setEmail("admin@gmail.com");
            admin.setMobileNumber("8483079733");

            admin.setPassword(passwordEncoder.encode("admin@123"));
            admin.setRole(Role.ADMIN);

            adminRepository.save(admin);

            System.out.println("Default Admin Created");
        }
    }
}

