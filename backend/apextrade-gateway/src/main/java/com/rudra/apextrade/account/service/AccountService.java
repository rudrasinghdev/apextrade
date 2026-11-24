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

    public static final long DEFAULT_INITIAL_USDT = 1_000_000_000L;
    public static final long DEFAULT_INITIAL_BTC = 10_000_000_000L;

    private final ConcurrentHashMap<Long, Account> ledger = new ConcurrentHashMap<>();

    public void createAccount(long userId) {
        Account newAccount = new Account(DEFAULT_INITIAL_USDT, DEFAULT_INITIAL_BTC);
        Account existing = ledger.putIfAbsent(userId, newAccount);
        if (existing != null) {
            throw new AccountAlreadyExistsException("Account already exists for trader #" + userId);
        }
        log.info("[Ledger Audit] Paper trading account initialized for Trader #{}: {} USD, {} BTC.",
                userId, DEFAULT_INITIAL_USDT, DEFAULT_INITIAL_BTC);
    }

    public Account getAccount(long userId) {
        Account account = ledger.get(userId);
        if (account == null) {
            throw new AccountNotFoundException("Trading account not found for trader #" + userId);
        }
        return account;
    }

    public void reserveBuyMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        getAccount(userId).reserveBuyMargin(orderId, price, quantity, timestampNs);
    }

    public void reserveSellMargin(long userId, long orderId, long price, long quantity, long timestampNs) {
        getAccount(userId).reserveSellMargin(orderId, price, quantity, timestampNs);
    }

    public void settleBuyTrade(long userId, long orderId, long usdCost, long btcReceived) {
        getAccount(userId).settleBuyTrade(orderId, usdCost, btcReceived);
    }

    public void settleSellTrade(long userId, long orderId, long btcSold, long usdReceived) {
        getAccount(userId).settleSellTrade(orderId, btcSold, usdReceived);
    }

    public void cancelOrder(long userId, long orderId) {
        getAccount(userId).cancelOrder(orderId);
    }

    public void cancelOrder(long userId, long orderId, long unfilledQty) {
        getAccount(userId).cancelOrder(orderId, unfilledQty);
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
                formatUsdt(account.getAvailableUsdt()),
                formatUsdt(account.getReservedUsdt()),
                formatUsdt(account.getTotalUsdt()),
                formatBtc(account.getAvailableBtc()),
                formatBtc(account.getReservedBtc()),
                formatBtc(account.getTotalBtc()));
    }

    private static String formatUsdt(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatBtc(long satoshi) {
        return BigDecimal.valueOf(satoshi).movePointLeft(8).setScale(8, RoundingMode.UNNECESSARY).toPlainString();
    }
}
