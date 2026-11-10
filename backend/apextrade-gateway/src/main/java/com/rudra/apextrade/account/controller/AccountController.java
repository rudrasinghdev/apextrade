package com.rudra.apextrade.account.controller;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.auth.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(accountService.getBalance(principal.userId()));
    }
}
