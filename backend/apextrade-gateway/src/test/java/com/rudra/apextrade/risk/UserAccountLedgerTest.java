package com.rudra.apextrade.risk;

import com.rudra.apextrade.risk.model.AccountBalance;
import com.rudra.apextrade.risk.service.UserAccountLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UserAccountLedgerTest {

    private UserAccountLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new UserAccountLedger();
    }

    @Test
    @DisplayName("Risk Ledger: Explicit account creation initializes with default paper balance ($100,000 USD and 5 BTC)")
    void testInitialBalance() {
        ledger.createAccount(101L);
        AccountBalance balance = ledger.getAccount(101L);
        assertNotNull(balance, "Account must exist after explicit creation");
        assertEquals(UserAccountLedger.DEFAULT_INITIAL_USD, balance.getAvailableUsd());
        assertEquals(0L, balance.getReservedUsd());
        assertEquals(UserAccountLedger.DEFAULT_INITIAL_USD, balance.getTotalUsd());

        assertEquals(UserAccountLedger.DEFAULT_INITIAL_BTC, balance.getAvailableBtc());
        assertEquals(0L, balance.getReservedBtc());
        assertEquals(UserAccountLedger.DEFAULT_INITIAL_BTC, balance.getTotalBtc());
    }

    @Test
    @DisplayName("Risk Ledger: Successful USD reservation atomically moves funds to reserved")
    void testSuccessfulUsdReservation() {
        ledger.createAccount(101L);
        long cost = 2_000_000L;
        boolean reserved = ledger.reserveUsd(101L, cost);

        assertTrue(reserved, "USD margin reservation must succeed when available balance is sufficient");
        AccountBalance balance = ledger.getAccount(101L);
        assertEquals(8_000_000L, balance.getAvailableUsd());
        assertEquals(2_000_000L, balance.getReservedUsd());
        assertEquals(10_000_000L, balance.getTotalUsd());
    }

    @Test
    @DisplayName("Risk Ledger: Insufficient USD funds fast-fails and leaves available USD untouched")
    void testInsufficientUsdRejection() {
        ledger.createAccount(101L);
        long cost = 12_000_000L;
        boolean reserved = ledger.reserveUsd(101L, cost);

        assertFalse(reserved, "USD reservation must fail when cost exceeds balance");
        AccountBalance balance = ledger.getAccount(101L);
        assertEquals(10_000_000L, balance.getAvailableUsd());
        assertEquals(0L, balance.getReservedUsd());
    }

    @Test
    @DisplayName("Risk Ledger: Releasing USD funds on order cancel restores available balance")
    void testReleaseUsd() {
        ledger.createAccount(101L);
        long cost = 4_000_000L;
        ledger.reserveUsd(101L, cost);
        ledger.releaseUsd(101L, cost);

        AccountBalance balance = ledger.getAccount(101L);
        assertEquals(10_000_000L, balance.getAvailableUsd());
        assertEquals(0L, balance.getReservedUsd());
    }

    @Test
    @DisplayName("Risk Ledger: Successful BTC reservation and release for SELL orders")
    void testBtcReservationAndRelease() {
        ledger.createAccount(101L);
        long btcAmount = 200_000_000L; // 2 BTC
        boolean reserved = ledger.reserveBtc(101L, btcAmount);

        assertTrue(reserved, "BTC reservation must succeed when holding 5 BTC");
        AccountBalance balance = ledger.getAccount(101L);
        assertEquals(300_000_000L, balance.getAvailableBtc());
        assertEquals(200_000_000L, balance.getReservedBtc());

        // Cancel order releases BTC
        ledger.releaseBtc(101L, btcAmount);
        assertEquals(500_000_000L, balance.getAvailableBtc());
        assertEquals(0L, balance.getReservedBtc());
    }

    @Test
    @DisplayName("Risk Ledger: Settle Buy Trade deducts reserved USD and credits acquired BTC")
    void testSettleBuyTrade() {
        AccountBalance balance = new AccountBalance(100_000L, 0L);
        balance.reserveUsd(50_000L);
        assertEquals(50_000L, balance.getAvailableUsd());
        assertEquals(50_000L, balance.getReservedUsd());

        // S2 Matching Engine executes the match: 1 BTC received for $50k USD
        balance.settleBuyTrade(50_000L, 100_000_000L);

        assertEquals(50_000L, balance.getAvailableUsd());
        assertEquals(0L, balance.getReservedUsd());
        assertEquals(100_000_000L, balance.getAvailableBtc());
    }

    @Test
    @DisplayName("Risk Ledger: Settle Sell Trade deducts reserved BTC and credits acquired USD")
    void testSettleSellTrade() {
        AccountBalance balance = new AccountBalance(0L, 100_000_000L); // 1 BTC
        balance.reserveBtc(100_000_000L);
        assertEquals(0L, balance.getAvailableBtc());
        assertEquals(100_000_000L, balance.getReservedBtc());

        // S2 Matching Engine executes the match: Sold 1 BTC for $60k USD
        balance.settleSellTrade(100_000_000L, 60_000L);

        assertEquals(0L, balance.getAvailableBtc());
        assertEquals(0L, balance.getReservedBtc());
        assertEquals(60_000L, balance.getAvailableUsd());
    }

    @Test
    @DisplayName("Risk Ledger Concurrency: 10 concurrent threads competing for limited balance never overdraw (CAS safety)")
    void testConcurrentOverdrawProtection() throws InterruptedException {
        AccountBalance balance = new AccountBalance(1_000L, 0L);

        int threadCount = 10;
        long orderCost = 200L; // each wants 200 USD (only 5 can succeed out of 10)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (balance.reserveUsd(orderCost)) {
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
        assertEquals(0L, balance.getAvailableUsd(), "Available balance must not be negative");
        assertEquals(1_000L, balance.getReservedUsd(), "Reserved USD must equal exactly 1,000");
    }
}
