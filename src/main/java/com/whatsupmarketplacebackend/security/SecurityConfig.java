package com.whatsupmarketplacebackend.security;

import com.whatsupmarketplacebackend.dto.ApiResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable) // safe: stateless, token-based API, no browser session/cookie auth
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // 1. Swagger UI & API Docs (Public)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 2. Auth Endpoints (Public, except logout which requires authentication)
                        .requestMatchers("/auth/logout").authenticated()
                        .requestMatchers("/auth/**").permitAll()

                        // 3. Public Client, Enquiry & Public Feedback Fetch Endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/client/registration",
                                "/api/client/delete-account",
                                "/api/enquirie/create"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/feedback/all",
                                "/api/feedback/*",
                                "/api/plans/**"
                        ).permitAll()

                        // 4. Feedback Management Endpoints (CLIENT & ADMIN)
                        .requestMatchers(
                                "/api/feedback/create/**",
                                "/api/feedback/update/**",
                                "/api/feedback/delete/**"
                        ).hasRole("CLIENT")

                        // 5. Client Profile Operations (CLIENT & ADMIN)
                        .requestMatchers(
                                "/api/client/**"
                        ).hasAnyRole("CLIENT", "ADMIN")

                        // 6. Subscription & Billing Operations (CLIENT & ADMIN)
                        .requestMatchers(
                                "/api/subscription/**",
                                "/api/customer-data/client/**",
                                "/api/upload/**"
                        ).hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/whatsapp-templates/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/whatsapp-templates/**"
                        ).hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(
                                "/api/customer-data/import"
                        ).hasAnyRole("ADMIN","CLIENT")
                        .requestMatchers(
                                "/api/plans/**",
                                "/api/payment/**"
                        ).hasRole("CLIENT")

                        // 7. Admin Management Operations (ADMIN Only)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 8. All other requests require authentication
                        .anyRequest().authenticated()
                )

                // â”€â”€ 401 Unauthorized Handler â”€â”€
                // Triggered when: no token, expired token, invalid token, blacklisted token
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            // Use the specific error message set by JwtAuthenticationFilter
                            String error = (String) request.getAttribute("error");
                            String message;

                            if (error != null) {
                                // Map filter-level error attributes to user-friendly messages
                                message = switch (error) {
                                    case "Token Expired" ->
                                            "Your session has expired. Please login again.";
                                    case "Invalid Token" ->
                                            "Invalid authentication token. Please login again.";
                                    case "Token has been invalidated" ->
                                            "Your session has been logged out. Please login again.";
                                    default -> error;
                                };
                            } else {
                                // No token was provided at all
                                message = "Authentication required. Please provide a valid token.";
                            }

                            ApiResponseDTO<Object> body = new ApiResponseDTO<>("FAIL", message, null);
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })

                        // â”€â”€ 403 Forbidden / Access Denied Handler â”€â”€
                        // Triggered when: valid token but wrong role (e.g. CLIENT accessing ADMIN endpoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            ApiResponseDTO<Object> body = new ApiResponseDTO<>(
                                    "FAIL",
                                    "Access denied. You do not have permission to access this resource.",
                                    null
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://frontend-domain.com", "http://localhost:5173/"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
