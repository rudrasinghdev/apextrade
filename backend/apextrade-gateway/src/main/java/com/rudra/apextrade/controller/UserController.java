package com.rudra.apextrade.controller;

import com.rudra.apextrade.dto.UserProfileResponse;
import com.rudra.apextrade.security.UserPrincipal;
import com.rudra.apextrade.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }
        UserProfileResponse profile = userService.getProfile(principal.userId());
        return ResponseEntity.ok(profile);
    }
}

