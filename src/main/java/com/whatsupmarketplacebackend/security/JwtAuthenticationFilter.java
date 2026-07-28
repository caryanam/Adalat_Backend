package com.whatsupmarketplacebackend.security;

import com.whatsupmarketplacebackend.enums.Role;
import com.whatsupmarketplacebackend.repository.AdminRepository;
import com.whatsupmarketplacebackend.repository.ClientRepository;
import com.whatsupmarketplacebackend.service.serviceImpl.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AdminRepository adminRepository;
    private final ClientRepository clientRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            try {
                Jws<Claims> parsed = jwtService.parse(token);
                Claims payload = parsed.getPayload();

                // Check if the token has been blacklisted (user logged out)
                String jti = payload.getId();
                if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                    log.info("Rejected blacklisted JWT with jti={}", jti);
                    request.setAttribute("error", "Token has been invalidated");
                    filterChain.doFilter(request, response);
                    return;
                }

                Long id = Long.valueOf(payload.getSubject());
                Role role = Role.valueOf(payload.get("role", String.class));

                Optional<CustomUserDetails> maybeUserDetails = resolve(id, role);

                if (maybeUserDetails.isEmpty()) {
                    log.warn("JWT subject {} ({}) does not match any existing account", id, role);
                    request.setAttribute("error", "Invalid Token");
                } else {
                    CustomUserDetails userDetails = maybeUserDetails.get();

                    if (SecurityContextHolder.getContext().getAuthentication() == null) {

//                        UsernamePasswordAuthenticationToken authentication =
//                                new UsernamePasswordAuthenticationToken(
//                                        userDetails.getUsername(),
//                                        null,
//                                        userDetails.getAuthorities()
//                                );

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }

            } catch (ExpiredJwtException e) {
                log.info("Rejected expired JWT: {}", e.getMessage());
                request.setAttribute("error", "Token Expired");
            } catch (IllegalArgumentException e) {
                // IllegalArgumentException also covers Role.valueOf() on an unknown value
                log.warn("JWT subject/role claim was invalid");
                request.setAttribute("error", "Invalid Token");
            } catch (JwtException e) {
                // Covers signature failures, malformed tokens, etc.
                // Deliberately not logging the token itself here or anywhere above â€”
                // bearer tokens are credentials and must never land in logs.
                log.warn("Rejected invalid JWT: {}", e.getMessage());
                request.setAttribute("error", "Invalid Token");
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<CustomUserDetails> resolve(Long id, Role role) {
        if (role == Role.ADMIN) {
            return adminRepository.findById(id)
                    .map(a -> new CustomUserDetails(a.getAdminId(), a.getEmail(), a.getPassword(), Role.ADMIN));
        }
        return clientRepository.findById(id)
                .map(c -> new CustomUserDetails(c.getId(), c.getEmail(), c.getPassword(), Role.CLIENT));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();
        String method = request.getMethod();

        return (path.startsWith("/auth/") && !path.equals("/auth/logout"))
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/api/client/registration")
                || path.equals("/api/enquirie/create")
                || path.equals("/api/client/delete-account")
                || ("GET".equalsIgnoreCase(method) && path.startsWith("/api/feedback/"));
    }
}
