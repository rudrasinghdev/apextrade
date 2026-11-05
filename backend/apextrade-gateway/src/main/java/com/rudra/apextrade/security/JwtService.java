package com.rudra.apextrade.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {

    private final SecretKey key;
    private final JwtParser jwtParser;
    private final long expirationMs;

    public JwtService(
            @Value("${apextrade.jwt.secret}") String secretKey,
            @Value("${apextrade.jwt.expiration-ms}") long expirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser().verifyWith(this.key).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(long userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", "USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    public Optional<UserPrincipal> parseAndValidate(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            String email = claims.getSubject();
            long userId = claims.get("userId", Number.class).longValue();
            String role = claims.get("role", String.class);

            return Optional.of(new UserPrincipal(userId, email, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}