package com.rudra.apextrade.auth.service;

import com.rudra.apextrade.auth.dto.LoginRequest;
import com.rudra.apextrade.auth.dto.LoginResponse;
import com.rudra.apextrade.auth.dto.RegisterRequest;
import com.rudra.apextrade.auth.dto.RegisterResponse;
import com.rudra.apextrade.auth.security.JwtService;
import com.rudra.apextrade.user.exception.UserAlreadyExistsException;
import com.rudra.apextrade.user.model.User;
import com.rudra.apextrade.user.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService tokenProvider;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService tokenProvider
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public RegisterResponse register(RegisterRequest req) {
        if (req.email() == null || req.email().trim().isEmpty()) {
            throw new IllegalArgumentException("Email must not be empty");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        String email = req.email().trim().toLowerCase();

        if (userService.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(req.password());
        User user = userService.createUser(email, passwordHash);

        return new RegisterResponse(user.getEmail(), user.getUserId());
    }

    public LoginResponse login(LoginRequest req) {
        User user = userService.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(user.getUserId(), user.getEmail());
        return new LoginResponse(user.getEmail(), user.getUserId(), token);
    }
}
