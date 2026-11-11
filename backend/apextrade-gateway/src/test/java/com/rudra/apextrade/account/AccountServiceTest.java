package com.rudra.apextrade.account;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService();
    }

    @Test
    @DisplayName("Account Ledger: Account creation initializes with default paper balance ($100,000 USD and 5 BTC)")
    void testInitialBalance() {
        accountService.createAccount(101L);
        Account account = accountService.getAccount(101L);
        assertNotNull(account, "Account must exist after creation");
        assertEquals(AccountService.DEFAULT_INITIAL_USD, account.getAvailableUsd());
        assertEquals(0L, account.getReservedUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_USD, account.getTotalUsd());

        assertEquals(AccountService.DEFAULT_INITIAL_BTC, account.getAvailableBtc());
        assertEquals(0L, account.getReservedBtc());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC, account.getTotalBtc());
    }

    @Test
    @DisplayName("Account Ledger: getAccount throws AccountNotFoundException for uncreated account")
    void testGetAccountNotFound() {
        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(999L));
    }

    @Test
    @DisplayName("Account Ledger: createAccount throws AccountAlreadyExistsException on duplicate creation")
    void testDuplicateAccountCreationThrows() {
        accountService.createAccount(101L);
        assertThrows(AccountAlreadyExistsException.class, () -> accountService.createAccount(101L));
    }

    @Test
    @DisplayName("Account Ledger Facade: reserveBuyMargin atomically locks margin")
    void testReserveBuyMarginFacade() {
        accountService.createAccount(101L);
        // 50,000 USD * 40 units = 2,000,000 USD margin
        boolean reserved = accountService.reserveBuyMargin(101L, 1L, 50_000L, 40L, System.nanoTime());

        assertTrue(reserved);
        AccountBalanceResponse balance = accountService.getBalance(101L);
        assertEquals(8_000_000L, balance.availableUsd());
        assertEquals(2_000_000L, balance.reservedUsd());
        assertTrue(accountService.hasActiveOrder(101L, 1L));
    }

    @Test
    @DisplayName("Account Ledger Facade: cancelOrder releases reserved margin")
    void testCancelOrderFacade() {
        accountService.createAccount(101L);
        accountService.reserveBuyMargin(101L, 1L, 50_000L, 40L, System.nanoTime());
        accountService.cancelOrder(101L, 1L);

        AccountBalanceResponse balance = accountService.getBalance(101L);
        assertEquals(10_000_000L, balance.availableUsd());
        assertEquals(0L, balance.reservedUsd());
        assertFalse(accountService.hasActiveOrder(101L, 1L));
    }

    @Test
    @DisplayName("Account Concurrency: 10 concurrent threads competing for limited balance never overdraw (CAS safety)")
    void testConcurrentOverdrawProtection() throws InterruptedException {
        Account account = new Account(1_000L, 0L);

        int threadCount = 10;
        // each wants 200 USD margin (only 5 can succeed out of 10)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long orderId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (account.reserveBuyMargin(orderId, 200L, 1L, System.nanoTime())) {
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
        assertEquals(5, account.getActiveOrders().size());
    }

    @Test
    @DisplayName("Partial Fill & Settle: Partial match maintains active order until fully filled")
    void testPartialFillAndSettlement() {
        accountService.createAccount(101L);
        // Buyer places order for 10 BTC @ 50,000 USD = 500,000 USD margin
        accountService.reserveBuyMargin(101L, 1L, 50_000L, 10L, System.nanoTime());

        Account account = accountService.getAccount(101L);
        assertEquals(500_000L, account.getReservedUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_USD - 500_000L, account.getAvailableUsd());
        assertEquals(1, accountService.getActiveOrders(101L).size());

        // Partial match 1: 3 BTC @ 50,000 USD = 150,000 USD cost
        accountService.settleBuyTrade(101L, 1L, 150_000L, 3L);

        // Order should STILL be active with 7 BTC remaining
        assertTrue(accountService.hasActiveOrder(101L, 1L));
        assertEquals(1, accountService.getActiveOrders(101L).size());
        assertEquals(7L, accountService.getActiveOrders(101L).iterator().next().getRemainingQuantity());
        assertEquals(350_000L, account.getReservedUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 3L, account.getAvailableBtc());

        // Partial match 2: 7 BTC @ 50,000 USD = 350,000 USD cost (fully filled)
        accountService.settleBuyTrade(101L, 1L, 350_000L, 7L);

        // Order should now be completely removed from active orders
        assertFalse(accountService.hasActiveOrder(101L, 1L));
        assertEquals(0, accountService.getActiveOrders(101L).size());
        assertEquals(0L, account.getReservedUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 10L, account.getAvailableBtc());
    }

    @Test
    @DisplayName("Partial Fill Then Cancel: Cancel releases remaining margin after partial fill")
    void testPartialFillThenCancel() {
        accountService.createAccount(101L);
        // Buyer places order for 10 BTC @ 50,000 USD = 500,000 USD margin
        accountService.reserveBuyMargin(101L, 1L, 50_000L, 10L, System.nanoTime());

        // Partial match: 4 BTC filled, cost 200,000 USD
        accountService.settleBuyTrade(101L, 1L, 200_000L, 4L);

        // Cancel remaining 6 BTC
        accountService.cancelOrder(101L, 1L);

        Account account = accountService.getAccount(101L);
        assertFalse(accountService.hasActiveOrder(101L, 1L));
        assertEquals(0L, account.getReservedUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_USD - 200_000L, account.getAvailableUsd());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 4L, account.getAvailableBtc());
    }

    @Test
    @DisplayName("Get Active Order By ID: O(1) single-order lookup and null check")
    void testGetActiveOrderById() {
        accountService.createAccount(101L);
        accountService.reserveBuyMargin(101L, 42L, 50_000L, 5L, System.nanoTime());

        var order = accountService.getActiveOrder(101L, 42L);
        assertNotNull(order);
        assertEquals(42L, order.getOrderId());
        assertEquals(5L, order.getInitialQuantity());
        assertEquals(5L, order.getRemainingQuantity());

        assertNull(accountService.getActiveOrder(101L, 999L), "Non-existent order should return null");

        accountService.cancelOrder(101L, 42L);
        assertNull(accountService.getActiveOrder(101L, 42L), "Cancelled order should no longer be active");
    }
}