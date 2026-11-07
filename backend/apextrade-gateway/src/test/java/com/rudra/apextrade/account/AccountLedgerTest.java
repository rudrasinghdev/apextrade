package com.rudra.apextrade.account;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
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
    @DisplayName("Account Ledger: getAccount throws AccountNotFoundException for uncreated account")
    void testGetAccountNotFound() {
        assertThrows(AccountNotFoundException.class, () -> ledger.getAccount(999L));
    }

    @Test
    @DisplayName("Account Ledger: createAccount throws AccountAlreadyExistsException on duplicate creation")
    void testDuplicateAccountCreationThrows() {
        ledger.createAccount(101L);
        assertThrows(AccountAlreadyExistsException.class, () -> ledger.createAccount(101L));
    }

    @Test
    @DisplayName("Account Ledger Facade: reserveBuyMargin atomically locks margin")
    void testReserveBuyMarginFacade() {
        ledger.createAccount(101L);
        long cost = 2_000_000L;
        boolean reserved = ledger.reserveBuyMargin(101L, 1L, cost);

        assertTrue(reserved);
        AccountBalanceResponse balance = ledger.getBalance(101L);
        assertEquals(8_000_000L, balance.availableUsd());
        assertEquals(2_000_000L, balance.reservedUsd());
        assertTrue(ledger.getActiveOrderIds(101L).contains(1L));
    }

    @Test
    @DisplayName("Account Ledger Facade: cancelOrder releases reserved margin")
    void testCancelOrderFacade() {
        ledger.createAccount(101L);
        ledger.reserveBuyMargin(101L, 1L, 2_000_000L);
        boolean cancelled = ledger.cancelOrder(101L, 1L);

        assertTrue(cancelled);
        AccountBalanceResponse balance = ledger.getBalance(101L);
        assertEquals(10_000_000L, balance.availableUsd());
        assertEquals(0L, balance.reservedUsd());
        assertFalse(ledger.getActiveOrderIds(101L).contains(1L));
    }

    @Test
    @DisplayName("Account Concurrency: 10 concurrent threads competing for limited balance never overdraw (CAS safety)")
    void testConcurrentOverdrawProtection() throws InterruptedException {
        Account account = new Account(1_000L, 0L);

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

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        assertEquals(5, successCount.get(), "Exactly 5 orders can be funded ($1,000 / $200)");
        assertEquals(0L, account.getAvailableUsd(), "Available balance must not be negative");
        assertEquals(1_000L, account.getReservedUsd(), "Reserved USD must equal exactly 1,000");
        assertEquals(5, account.getActiveOrderIds().size());
    }
}
