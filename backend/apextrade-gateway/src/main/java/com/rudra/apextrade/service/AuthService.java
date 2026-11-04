package com.rudra.apextrade.service;

import com.rudra.apextrade.dto.LoginRequest;
import com.rudra.apextrade.dto.LoginResponse;
import com.rudra.apextrade.dto.RegisterRequest;
import com.rudra.apextrade.dto.RegisterResponse;
import com.rudra.apextrade.model.User;
import com.rudra.apextrade.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Security orchestration service managing authentication, credentials verification,
 * and token issuance. Delegates User persistence and domain lifecycle to UserService.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public RegisterResponse register(RegisterRequest req) {
        if (req.username() == null || req.username().trim().isEmpty()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (req.email() == null || req.email().trim().isEmpty()) {
            throw new IllegalArgumentException("Email must not be empty");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();

        if (userService.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userService.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(req.password());
        User user = userService.createUser(username, email, passwordHash, "TRADER");

        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getEmail());
        return new RegisterResponse(user.getId(), user.getUsername(), user.getEmail(), token);
    }

    public LoginResponse login(LoginRequest req) {
        User user = userService.findByUsername(req.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getEmail());
        return new LoginResponse(token, "Bearer", user.getId(), user.getUsername(), user.getEmail());
    }
}
