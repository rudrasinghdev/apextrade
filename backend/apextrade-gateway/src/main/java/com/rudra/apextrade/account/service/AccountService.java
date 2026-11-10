package com.rudra.apextrade.account.service;

import  com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.model.ActiveOrder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    public static final long DEFAULT_INITIAL_USD = 10_000_000L;
    public static final long DEFAULT_INITIAL_BTC = 500_000_000L;

    private final ConcurrentHashMap<Long, Account> ledger = new ConcurrentHashMap<>();

    public void createAccount(long userId) {
        Account newAccount = new Account(DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC);
        Account existing = ledger.putIfAbsent(userId, newAccount);
        if (existing != null) {
            throw new AccountAlreadyExistsException("Account already exists for trader #" + userId);
        }
    }

    public Account getAccount(long userId) {
        Account account = ledger.get(userId);
        if (account == null) {
            throw new AccountNotFoundException("Trading account not found for trader #" + userId);
        }
        return account;
    }

    public boolean reserveBuyMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        return getAccount(userId).reserveBuyMargin(orderId, price, quantity, timestampNs);
    }

    public boolean reserveSellMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        return getAccount(userId).reserveSellMargin(orderId, price, quantity, timestampNs);
    }

    public void cancelOrder(long userId, long orderId) {
        getAccount(userId).cancelOrder(orderId);
    }

    public void settleBuyTrade(long userId, long orderId, long usdCost, long btcReceived) {
        Account account = ledger.get(userId);
        if (account != null) {
            account.settleBuyTrade(orderId, usdCost, btcReceived);
        }
    }

    public void settleSellTrade(long userId, long orderId, long btcSold, long usdReceived) {
        Account account = ledger.get(userId);
        if (account != null) {
            account.settleSellTrade(orderId, btcSold, usdReceived);
        }
    }

    public boolean hasActiveOrder(long userId, long orderId) {
        Account account = ledger.get(userId);
        return account != null && account.hasActiveOrder(orderId);
    }

    public ActiveOrder getActiveOrder(long userId, long orderId) {
        return getAccount(userId).getActiveOrder(orderId);
    }

    public Collection<ActiveOrder> getActiveOrders(long userId) {
        return getAccount(userId).getActiveOrders();
    }

    public AccountBalanceResponse getBalance(long userId) {
        Account account = getAccount(userId);
        return new AccountBalanceResponse(
                userId,
                account.getAvailableUsd(),
                account.getReservedUsd(),
                account.getTotalUsd(),
                account.getAvailableBtc(),
                account.getReservedBtc(),
                account.getTotalBtc()
        );
    }
}
