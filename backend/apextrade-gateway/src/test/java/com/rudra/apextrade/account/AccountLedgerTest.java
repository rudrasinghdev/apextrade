package com.rudra.apextrade.account;

import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.service.AccountLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AccountLedgerTest {

    private AccountLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new AccountLedger();
    }

    @Test
    @DisplayName("Account Ledger: Account creation initializes with default paper balance ($100,000 USD and 5 BTC)")
    void testInitialBalance() {
        ledger.createAccount(101L);
        Account account = ledger.getAccount(101L);
        assertNotNull(account, "Account must exist after creation");
        assertEquals(AccountLedger.DEFAULT_INITIAL_USD, account.getAvailableUsd());
        assertEquals(0L, account.getReservedUsd());
        assertEquals(AccountLedger.DEFAULT_INITIAL_USD, account.getTotalUsd());

        assertEquals(AccountLedger.DEFAULT_INITIAL_BTC, account.getAvailableBtc());
        assertEquals(0L, account.getReservedBtc());
        assertEquals(AccountLedger.DEFAULT_INITIAL_BTC, account.getTotalBtc());
    }

    @Test
    @DisplayName("Account: placeBuyOrder atomically reserves USD and records active order ID")
    void testPlaceBuyOrder() {
        Account account = ledger.getAccount(101L);
        long cost = 2_000_000L;
        boolean placed = account.placeBuyOrder(1L, cost);

        assertTrue(placed, "BUY order placement must succeed when available balance is sufficient");
        assertEquals(8_000_000L, account.getAvailableUsd());
        assertEquals(2_000_000L, account.getReservedUsd());
        assertTrue(account.hasActiveOrder(1L));
        assertTrue(account.getActiveOrderIds().contains(1L));
    }

    @Test
    @DisplayName("Account: placeBuyOrder fails fast when insufficient USD and leaves available untouched")
    void testInsufficientUsdRejection() {
        Account account = ledger.getAccount(101L);
        long cost = 12_000_000L;
        boolean placed = account.placeBuyOrder(2L, cost);

        assertFalse(placed, "BUY order must fail when cost exceeds balance");
        assertEquals(10_000_000L, account.getAvailableUsd());
        assertEquals(0L, account.getReservedUsd());
        assertFalse(account.hasActiveOrder(2L));
    }

    @Test
    @DisplayName("Account: cancelOrder releases reserved funds and removes order ID")
    void testCancelOrder() {
        Account account = ledger.getAccount(101L);
        long cost = 4_000_000L;
        account.placeBuyOrder(3L, cost);
        boolean cancelled = account.cancelOrder(3L);

        assertTrue(cancelled, "cancelOrder must succeed for active order");
        assertEquals(10_000_000L, account.getAvailableUsd());
        assertEquals(0L, account.getReservedUsd());
        assertFalse(account.hasActiveOrder(3L));
    }

    @Test
    @DisplayName("Account: placeSellOrder and cancelOrder for BTC inventory")
    void testBtcOrderAndCancel() {
        Account account = ledger.getAccount(101L);
        long btcAmount = 200_000_000L; // 2 BTC
        boolean placed = account.placeSellOrder(4L, btcAmount);

        assertTrue(placed, "SELL order must succeed when holding 5 BTC");
        assertEquals(300_000_000L, account.getAvailableBtc());
        assertEquals(200_000_000L, account.getReservedBtc());
        assertTrue(account.hasActiveOrder(4L));

        boolean cancelled = account.cancelOrder(4L);
        assertTrue(cancelled);
        assertEquals(500_000_000L, account.getAvailableBtc());
        assertEquals(0L, account.getReservedBtc());
        assertFalse(account.hasActiveOrder(4L));
    }

    @Test
    @DisplayName("Account Concurrency: 10 concurrent threads competing for limited balance never overdraw (CAS safety)")
    void testConcurrentOverdrawProtection() throws InterruptedException {
        Account account = new Account(101L, 1_000L, 0L);

        int threadCount = 10;
        long orderCost = 200L; // each wants 200 USD (only 5 can succeed out of 10)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long orderId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (account.placeBuyOrder(orderId, orderCost)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 10 threads concurrently
        finishLatch.await();
        executor.shutdown();

        assertEquals(5, successCount.get(), "Exactly 5 orders can be funded ($1,000 / $200)");
        assertEquals(0L, account.getAvailableUsd(), "Available balance must not be negative");
        assertEquals(1_000L, account.getReservedUsd(), "Reserved USD must equal exactly 1,000");
        assertEquals(5, account.getActiveOrderIds().size());
    }
}

