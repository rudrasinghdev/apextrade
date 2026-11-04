package com.rudra.apextrade.service;

import com.rudra.apextrade.dto.UserProfileResponse;
import com.rudra.apextrade.model.User;
import com.rudra.apextrade.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String username, String email, String passwordHash, String role) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .role(role)
                .createdAt(System.currentTimeMillis())
                .build();
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(long userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}

