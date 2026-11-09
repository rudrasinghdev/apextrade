package com.rudra.apextrade.account.service;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.model.ActiveOrder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountLedger {

    public static final long DEFAULT_INITIAL_USD = 10_000_000L;
    public static final long DEFAULT_INITIAL_BTC = 500_000_000L;

    private final ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();

    public void createAccount(long userId) {
        Account newAccount = new Account(DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC);
        Account existing = accounts.putIfAbsent(userId, newAccount);
        if (existing != null) {
            throw new AccountAlreadyExistsException("Account already exists for trader #" + userId);
        }
    }

    public Account getAccount(long userId) {
        Account account = accounts.get(userId);
        if (account == null) {
            throw new AccountNotFoundException("Trading account not found for trader #" + userId);
        }
        return account;
    }

    public boolean reserveBuyMargin(long userId, long orderId, long usdCost) {
        return getAccount(userId).placeBuyOrder(orderId, usdCost);
    }

    public boolean reserveBuyMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        return getAccount(userId).placeBuyOrder(orderId, price, quantity, timestampNs);
    }

    public boolean reserveSellMargin(long userId, long orderId, long btcQuantity) {
        return getAccount(userId).placeSellOrder(orderId, btcQuantity);
    }

    public boolean reserveSellMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        return getAccount(userId).placeSellOrder(orderId, price, quantity, timestampNs);
    }

    public boolean cancelOrder(long userId, long orderId) {
        return getAccount(userId).cancelOrder(orderId);
    }

    public boolean hasActiveOrder(long userId, long orderId) {
        Account account = accounts.get(userId);
        return account != null && account.hasActiveOrder(orderId);
    }

    public Collection<ActiveOrder> getActiveOrders(long userId) {
        return getAccount(userId).getActiveOrders();
    }

    public void settleBuyTrade(long userId, long orderId, long usdCost, long btcReceived) {
        Account account = accounts.get(userId);
        if (account != null) {
            account.settleBuyTrade(orderId, usdCost, btcReceived);
        }
    }

    public void settleSellTrade(long userId, long orderId, long btcSold, long usdReceived) {
        Account account = accounts.get(userId);
        if (account != null) {
            account.settleSellTrade(orderId, btcSold, usdReceived);
        }
    }

    public Set<Long> getActiveOrderIds(long userId) {
        return getAccount(userId).getActiveOrderIds();
    }

    public AccountBalanceResponse getBalance(long userId) {
        Account a = getAccount(userId);
        return new AccountBalanceResponse(
                userId,
                a.getAvailableUsd(),
                a.getReservedUsd(),
                a.getTotalUsd(),
                a.getAvailableBtc(),
                a.getReservedBtc(),
                a.getTotalBtc()
        );
    }
}
