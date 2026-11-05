package com.rudra.apextrade.service;

import com.rudra.apextrade.dto.UserProfileResponse;
import com.rudra.apextrade.exception.UserNotFoundException;
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
    public User createUser(String email, String passwordHash, String role) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .role(role)
                .createdAt(System.currentTimeMillis())
                .build();
        return userRepository.save(user);
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
                user.getRole(),
                user.getCreatedAt()
        );
    }
}

