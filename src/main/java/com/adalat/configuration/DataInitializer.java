package com.adalat.configuration;

import com.adalat.entity.*;
import com.adalat.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking database initialization...");

        // 1. Admin Account

        if (adminRepository.findByEmail("admin@gmail.com").isEmpty()) {
            Admin admin = Admin.builder()
                    .fullName("System Admin")
                    .email("admin@gmail.com")
                    .mobileNumber("9867865788")
                    .password(passwordEncoder.encode("admin@123"))
                    .build();
            adminRepository.save(admin);
        }
        log.info("Default admins seeded.");


    }
}
