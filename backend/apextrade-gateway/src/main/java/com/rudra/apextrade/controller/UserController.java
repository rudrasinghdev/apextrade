package com.rudra.apextrade.controller;

import com.rudra.apextrade.dto.AccountBalanceResponse;
import com.rudra.apextrade.dto.UserProfileResponse;
import com.rudra.apextrade.risk.model.AccountBalance;
import com.rudra.apextrade.risk.service.UserAccountLedger;
import com.rudra.apextrade.security.UserPrincipal;
import com.rudra.apextrade.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserAccountLedger ledger;

    public UserController(UserService userService, UserAccountLedger ledger) {
        this.userService = userService;
        this.ledger = ledger;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        UserProfileResponse profile = userService.getProfile(principal.userId());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@AuthenticationPrincipal UserPrincipal principal) {
        AccountBalance balance = ledger.getAccount(principal.userId());
        return ResponseEntity.ok(new AccountBalanceResponse(
                principal.userId(),
                balance.getAvailableUsd(),
                balance.getReservedUsd(),
                balance.getTotalUsd(),
                balance.getAvailableBtc(),
                balance.getReservedBtc(),
                balance.getTotalBtc()
        ));
    }
}
