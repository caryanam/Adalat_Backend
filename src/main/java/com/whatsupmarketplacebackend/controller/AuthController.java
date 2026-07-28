package com.whatsupmarketplacebackend.controller;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.whatsupmarketplacebackend.dto.ForgotPasswordRequestDTO;
import com.whatsupmarketplacebackend.dto.LoginRequest;
import com.whatsupmarketplacebackend.dto.ResetPasswordRequestDTO;
import com.whatsupmarketplacebackend.dto.TokenResponse;
import com.whatsupmarketplacebackend.dto.VerifyOtpRequestDTO;
import com.whatsupmarketplacebackend.security.CustomUserDetails;
import com.whatsupmarketplacebackend.security.JwtService;
import com.whatsupmarketplacebackend.service.PasswordResetService;
import com.whatsupmarketplacebackend.service.serviceImpl.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for User Login, Logout & Password Reset Management")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    @Operation(summary = "User Login API", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password"),
            @ApiResponse(responseCode = "403", description = "Account disabled or blocked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponseDTO<TokenResponse>> login(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticate(loginRequest);
        System.out.println(authentication);
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        String accessToken = jwtService.generateAccessToken(principal);

        TokenResponse tokenResponse = TokenResponse.of(
                accessToken,
                jwtService.getTokenExpiration(),
                principal.getUsername(),
                principal.getRole().name()
        );

        log.info("Successful login for id={}, role={}", principal.getId(), principal.getRole());

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Login Successfully",
                        tokenResponse
                )
        );

    }

    @PostMapping("/logout")
    @Operation(summary = "User Logout API", description = "Access Level: Authenticated (Token Required). Invalidates the current JWT token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing token")
    })
    public ResponseEntity<ApiResponseDTO<Object>> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                Jws<Claims> parsed = jwtService.parse(token);
                String jti = parsed.getPayload().getId();

                if (jti != null) {
                    tokenBlacklistService.blacklist(jti);
                    log.info("Token blacklisted successfully, jti={}", jti);
                }
            } catch (Exception e) {
                log.warn("Logout attempted with invalid token: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Logout Successfully",
                        null
                )
        );
    }


    //Forgot Password - Send OTP
    @PostMapping("/forgot-password")
    @Operation(summary = "Send OTP to email for password reset", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
            @ApiResponse(responseCode = "404", description = "Email not found")
    })
    public ResponseEntity<ApiResponseDTO<Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        log.info("Forgot password request for email: {}", request.email());
        passwordResetService.forgotPassword(request.email());

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "OTP sent to your email successfully.",
                        null
                )
        );
    }

    //Verify OTP
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP for password reset", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @ApiResponse(responseCode = "404", description = "No OTP found")
    })
    public ResponseEntity<ApiResponseDTO<Object>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDTO request) {

        log.info("OTP verification request for email: {}", request.email());
        passwordResetService.verifyOtp(request.email(), request.otp());

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "OTP verified successfully.",
                        null
                )
        );
    }

    //Reset Password
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using verified OTP", description = "Access Level: Public (No Token Required)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "OTP not verified or invalid"),
            @ApiResponse(responseCode = "404", description = "No OTP or account found")
    })
    public ResponseEntity<ApiResponseDTO<Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        log.info("Password reset request for email: {}", request.email());
        passwordResetService.resetPassword(
                request.email(),
                request.newPassword()
        );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        "SUCCESS",
                        "Password reset successfully.",
                        null
                )
        );
    }


    private Authentication authenticate(LoginRequest loginRequest) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));
        } catch (DisabledException e) {
            throw e;
        } catch (Exception e) {
            log.info("Failed login attempt for email={}", loginRequest.email());
            throw new BadCredentialsException("Invalid email or password");
        }
    }
}
