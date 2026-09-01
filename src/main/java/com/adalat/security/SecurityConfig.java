package com.adalat.security;

import com.adalat.dto.ApiResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/uploads/**"
                        ).permitAll()
                        .requestMatchers("/auth/logout", "/auth/me").authenticated()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(
                                "/api/customer/register",
                                "/api/customer/login",
                                "/api/customer/payment/initiate",
                                "/api/customer/payment/verify",
                                "/api/customer/registration",
                                "/api/lawyer/registration",
                                "/api/lawyer/register",
                                "/api/lawyers/register/**",
                                "/api/lawyers/*/documents",
                                "/api/lawyers/login"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/lawyers/**",
                                "/api/categories/**",
                                "/api/questions/**",
                                "/api/faqs/**",
                                "/api/legal-services/**"
                        ).permitAll()
                        .requestMatchers("/api/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/lawyer/**").hasAnyRole("LAWYER", "ADMIN")
                        .requestMatchers("/api/appointments/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/cases/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/lawyer-requests/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/service-orders/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/payments/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/chat/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/notifications/**").hasAnyRole("CUSTOMER", "LAWYER", "ADMIN")
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            String error = (String) request.getAttribute("error");
                            String message = error != null ? error : "Authentication required. Please provide a valid token.";
                            ApiResponseDTO<Object> body = new ApiResponseDTO<>("FAIL", message, null);
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
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
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "*"));
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
