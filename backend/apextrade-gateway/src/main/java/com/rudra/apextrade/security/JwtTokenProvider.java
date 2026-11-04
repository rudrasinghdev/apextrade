package com.rudra.apextrade.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${apextrade.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretHex,
            @Value("${apextrade.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        byte[] keyBytes;
        try {
            keyBytes = HexFormat.of().parseHex(secretHex);
        } catch (Exception e) {
            keyBytes = secretHex.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(long userId, String username, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getUserId(String token) {
        Claims claims = getClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(userIdObj.toString());
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }
}
