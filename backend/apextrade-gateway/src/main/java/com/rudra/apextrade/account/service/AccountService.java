package com.rudra.apextrade.account.service;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.model.ActiveOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AccountService {

    public static final long DEFAULT_INITIAL_USD = 1_000_000_000L;
    public static final long DEFAULT_INITIAL_BTC = 10_000_000_000L;

    private final ConcurrentHashMap<Long, Account> ledger = new ConcurrentHashMap<>();

    public void createAccount(long userId) {
        Account newAccount = new Account(DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC);
        Account existing = ledger.putIfAbsent(userId, newAccount);
        if (existing != null) {
            throw new AccountAlreadyExistsException("Account already exists for trader #" + userId);
        }
        log.info("[Ledger Audit] Paper trading account initialized for Trader #{}: {} USD, {} BTC.",
                userId, DEFAULT_INITIAL_USD, DEFAULT_INITIAL_BTC);
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

    public void cancelOrder(long userId, long orderId) {
        Account account = ledger.get(userId);
        if (account != null) {
            account.cancelOrder(orderId);
        }
    }

    public ActiveOrder getActiveOrder(long userId, long orderId) {
        Account account = ledger.get(userId);
        return account != null ? account.getActiveOrder(orderId) : null;
    }

    public Collection<ActiveOrder> getActiveOrders(long userId) {
        Account account = ledger.get(userId);
        return account != null ? account.getActiveOrders() : java.util.Collections.emptyList();
    }

    public AccountBalanceResponse getBalance(long userId) {
        Account account = getAccount(userId);
        return new AccountBalanceResponse(
                userId,
                formatUsd(account.getAvailableUsd()),
                formatUsd(account.getReservedUsd()),
                formatUsd(account.getTotalUsd()),
                formatBtc(account.getAvailableBtc()),
                formatBtc(account.getReservedBtc()),
                formatBtc(account.getTotalBtc()));
    }

    private static String formatUsd(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatBtc(long sats) {
        return BigDecimal.valueOf(sats).movePointLeft(8).setScale(8, RoundingMode.UNNECESSARY).toPlainString();
    }
}
