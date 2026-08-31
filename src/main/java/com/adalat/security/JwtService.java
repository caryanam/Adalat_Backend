package com.adalat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately NOT annotated with Lombok @Data / @Getter on the key field â€” a
 * generated toString()/equals() would serialize the raw SecretKey, a credential
 * leak risk the moment this bean ends up in a log line or debugger dump.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long tokenExpiration;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration}") long jwtExpiration
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            // HS512 requires a key of at least 512 bits (64 bytes)
            throw new IllegalArgumentException("security.jwt.secret must be at least 64 bytes for HS512");
        }
        if (jwtExpiration <= 0) {
            throw new IllegalArgumentException("security.jwt.expiration must be a positive number of seconds");
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpiration = jwtExpiration;
    }

    public long getTokenExpiration() {
        return tokenExpiration;
    }

    /**
     * Subject is the principal's id, which by itself is NOT globally unique
     * (Admin and Client are separate tables/sequences). The "role" claim is
     * what disambiguates which table that id refers to â€” always read both
     * together when validating a token, never the subject alone.
     */
    public String generateAccessToken(CustomUserDetails principal) {
        Instant now = Instant.now();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(tokenExpiration)))
                .claim("email", principal.getUsername())
                .claim("role", principal.getRole().name())
                .claim("typ", "access")
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Throws io.jsonwebtoken.JwtException (or a subclass, e.g. ExpiredJwtException,
     * SignatureException, MalformedJwtException) on any invalid token. Callers must
     * catch and translate â€” never let raw parse exceptions bubble to the client.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}

