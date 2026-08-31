package com.adalat.serviceImpl;

import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.LoginRequestDTO;
import com.adalat.security.CustomUserDetails;
import com.adalat.security.JwtService;
import com.adalat.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public AuthResponseDTO authenticate(LoginRequestDTO loginRequest) {
        // This will authenticate using either email or phone, based on what CustomUserDetailsService supports
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getIdentifier(), loginRequest.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        
        // Generate token
        String token = jwtService.generateAccessToken(userDetails);

        return AuthResponseDTO.builder()
                .token(token)
                .name(userDetails.getName())
                .role(userDetails.getRole().name())
                .build();
    }
}
