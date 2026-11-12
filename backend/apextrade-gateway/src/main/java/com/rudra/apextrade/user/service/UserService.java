package com.rudra.apextrade.user.service;

import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.user.dto.UserProfileResponse;
import com.rudra.apextrade.user.exception.UserNotFoundException;
import com.rudra.apextrade.user.model.User;
import com.rudra.apextrade.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public UserService(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @Transactional
    public User createUser(String email, String passwordHash) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .createdAt(System.currentTimeMillis())
                .build();
        User savedUser = userRepository.save(user);
        log.info("[User Audit] User #{} successfully registered with email: {}", savedUser.getUserId(), savedUser.getEmail());

        accountService.createAccount(savedUser.getUserId());
        return savedUser;
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return new UserProfileResponse(
                user.getEmail(),
                user.getUserId(),
                user.getCreatedAt()
        );
    }
}
