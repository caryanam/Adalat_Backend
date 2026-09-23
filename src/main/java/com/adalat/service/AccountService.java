package com.adalat.service;

import com.adalat.dto.DeleteAccountRequestDTO;
import com.adalat.dto.DeleteAccountResponseDTO;
import com.adalat.security.CustomUserDetails;

public interface AccountService {

    /**
     * Permanently deletes authenticated user's account and all associated entities & files.
     *
     * @param userDetails Authenticated user details from SecurityContext
     * @param request Delete request containing confirmation email/mobile and password
     * @return DeleteAccountResponseDTO indicating success or failure message
     */
    DeleteAccountResponseDTO deleteAccount(CustomUserDetails userDetails, DeleteAccountRequestDTO request);
}
