package com.rudra.apextrade.auth.service;

import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.auth.dto.LoginRequest;
import com.rudra.apextrade.auth.dto.LoginResponse;
import com.rudra.apextrade.auth.dto.RegisterRequest;
import com.rudra.apextrade.auth.dto.RegisterResponse;
import com.rudra.apextrade.auth.security.JwtService;
import com.rudra.apextrade.user.exception.UserAlreadyExistsException;
import com.rudra.apextrade.user.model.User;
import com.rudra.apextrade.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private final UserService userService;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserService userService,
            AccountService accountService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userService = userService;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();

        if (userService.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(req.password());
        User user = userService.createUser(email, passwordHash);

        return new RegisterResponse(user.getEmail(), user.getUserId());
    }

    public LoginResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        accountService.getOrCreateAccount(user.getUserId());

        String token = jwtService.generateToken(user.getUserId(), user.getEmail());
        log.info("[Auth Audit] Trader #{} ({}) authenticated successfully. JWT token issued.", user.getUserId(), user.getEmail());

        return new LoginResponse(user.getEmail(), user.getUserId(), token);
    }
}
