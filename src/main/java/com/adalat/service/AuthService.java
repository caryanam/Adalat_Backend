package com.adalat.service;

import com.adalat.dto.AuthResponseDTO;
import com.adalat.dto.LoginRequestDTO;

public interface AuthService {
    
    AuthResponseDTO authenticate(LoginRequestDTO loginRequest);

}
