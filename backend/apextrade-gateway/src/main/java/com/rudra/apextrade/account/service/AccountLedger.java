package com.rudra.apextrade.account.service;

import com.rudra.apextrade.account.model.Account;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe ledger for user accounts.
 * Operates strictly in RAM with zero database overhead on the order flight path.
 */
@Service
public class AccountLedger {

    public static final long DEFAULT_INITIAL_USD = 10_000_000L; // $100,000.00 (scale: 2)
    public static final long DEFAULT_INITIAL_BTC = 500_000_000L; // 5.00000000 BTC (scale: 8)

    private final ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();

    public void createAccount(long userId) {
        accounts.putIfAbsent(userId, new Account(userId, DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC));
    }

    public Account getAccount(long userId) {
        return accounts.computeIfAbsent(userId, id -> new Account(id, DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC));
    }

    public boolean hasAccount(long userId) {
        return accounts.containsKey(userId);
    }
}

