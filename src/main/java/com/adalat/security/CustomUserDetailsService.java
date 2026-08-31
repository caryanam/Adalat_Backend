package com.adalat.security;

import com.adalat.entity.Admin;
import com.adalat.entity.Customer;
import com.adalat.entity.Lawyer;
import com.adalat.enums.Role;
import com.adalat.repository.AdminRepository;
import com.adalat.repository.CustomerRepository;
import com.adalat.repository.LawyerRepository;
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
    private final CustomerRepository customerRepository;
    private final LawyerRepository lawyerRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. Check Admin table (by email or mobile)
        Optional<Admin> admin = adminRepository.findByEmail(username);
        if (admin.isEmpty()) {
            admin = adminRepository.findByMobileNumber(username);
        }
        if (admin.isPresent()) {
            Admin a = admin.get();
            return new CustomUserDetails(a.getAdminId(), a.getFullName(), a.getEmail(), a.getPassword(), Role.ADMIN);
        }

        // 2. Check Customer table (by email or mobile)
        Optional<Customer> customer = customerRepository.findByEmail(username);
        if (customer.isEmpty()) {
            customer = customerRepository.findByMobileNumber(username);
        }
        if (customer.isPresent()) {
            Customer c = customer.get();
            return new CustomUserDetails(c.getCustomerId(), c.getFullName(), c.getEmail(), c.getPassword(), Role.CUSTOMER);
        }

        // 3. Check Lawyer table (by email or mobile)
        Optional<Lawyer> lawyer = lawyerRepository.findByEmail(username);
        if (lawyer.isEmpty()) {
            lawyer = lawyerRepository.findByMobileNumber(username);
        }
        if (lawyer.isPresent()) {
            Lawyer l = lawyer.get();
            return new CustomUserDetails(l.getLawyerId(), l.getFullName(), l.getEmail(), l.getPassword(), Role.LAWYER);
        }

        throw new UsernameNotFoundException("User not found with email or mobile number: " + username);
    }
}
