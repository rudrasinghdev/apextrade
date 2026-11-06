package com.rudra.apextrade.account.controller;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.auth.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AccountController {

    private final AccountLedger ledger;

    public AccountController(AccountLedger ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@AuthenticationPrincipal UserPrincipal principal) {
        Account account = ledger.getAccount(principal.userId());
        return ResponseEntity.ok(new AccountBalanceResponse(
                principal.userId(),
                account.getAvailableUsd(),
                account.getReservedUsd(),
                account.getTotalUsd(),
                account.getAvailableBtc(),
                account.getReservedBtc(),
                account.getTotalBtc()
        ));
    }
}
