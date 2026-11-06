package com.rudra.apextrade.user.service;

import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.user.dto.UserProfileResponse;
import com.rudra.apextrade.user.exception.UserNotFoundException;
import com.rudra.apextrade.user.model.User;
import com.rudra.apextrade.user.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountLedger accountLedger;

    public UserService(UserRepository userRepository, AccountLedger accountLedger) {
        this.userRepository = userRepository;
        this.accountLedger = accountLedger;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateAccountsOnStartup() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (!accountLedger.hasAccount(user.getUserId())) {
                accountLedger.createAccount(user.getUserId());
            }
        }
    }

    @Transactional
    public User createUser(String email, String passwordHash) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .createdAt(System.currentTimeMillis())
                .build();
        User savedUser = userRepository.save(user);
        accountLedger.createAccount(savedUser.getUserId());
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
                user.getUserId(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
