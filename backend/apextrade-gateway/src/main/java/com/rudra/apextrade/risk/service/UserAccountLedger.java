package com.rudra.apextrade.risk.service;

import com.rudra.apextrade.risk.model.AccountBalance;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserAccountLedger {

    public static final long DEFAULT_INITIAL_USD = 10_000_000L;
    public static final long DEFAULT_INITIAL_BTC = 500_000_000L;

    private final ConcurrentHashMap<Long, AccountBalance> balances;

    public UserAccountLedger() {
        this.balances = new ConcurrentHashMap<>();
    }

    public void createAccount(long userId) {
        balances.putIfAbsent(userId, new AccountBalance(DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC));
    }

    public AccountBalance getAccount(long userId) {
        return balances.computeIfAbsent(userId, id -> new AccountBalance(DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC));
    }

    public AccountBalance getBalance(long userId) {
        return getAccount(userId);
    }

    public void depositUsd(long userId, long amount) {
        getAccount(userId).depositUsd(amount);
    }

    public boolean reserveUsd(long userId, long amount) {
        return getAccount(userId).reserveUsd(amount);
    }

    public void releaseUsd(long userId, long amount) {
        getAccount(userId).releaseUsd(amount);
    }

    public void depositBtc(long userId, long amount) {
        getAccount(userId).depositBtc(amount);
    }

    public boolean reserveBtc(long userId, long amount) {
        return getAccount(userId).reserveBtc(amount);
    }

    public void releaseBtc(long userId, long amount) {
        getAccount(userId).releaseBtc(amount);
    }

}
