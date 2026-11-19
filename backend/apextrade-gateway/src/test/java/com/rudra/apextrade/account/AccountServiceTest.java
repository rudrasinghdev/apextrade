package com.rudra.apextrade.account;

import com.rudra.apextrade.account.dto.AccountBalanceResponse;
import com.rudra.apextrade.account.exception.AccountAlreadyExistsException;
import com.rudra.apextrade.account.exception.AccountNotFoundException;
import com.rudra.apextrade.account.exception.InsufficientFundsException;
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
    @DisplayName("Account Ledger: Account creation initializes with default paper balance ($10,000,000 USDT and 100 BTC)")
    void testInitialBalance() {
        accountService.createAccount(101L);
        Account account = accountService.getAccount(101L);
        assertNotNull(account, "Account must exist after creation");
        assertEquals(AccountService.DEFAULT_INITIAL_USDT, account.getAvailableUsdt());
        assertEquals(0L, account.getReservedUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_USDT, account.getTotalUsdt());

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
        // 50,000 USDT (5,000,000 cents) * 40 BTC (4,000,000,000 sats) = 2,000,000 USDT (200,000,000 cents) margin
        accountService.reserveBuyMargin(101L, 1L, 5_000_000L, 4_000_000_000L, System.nanoTime());

        AccountBalanceResponse balance = accountService.getBalance(101L);
        assertEquals("8000000.00", balance.availableUsdt());
        assertEquals("2000000.00", balance.reservedUsdt());
        assertNotNull(accountService.getActiveOrder(101L, 1L));
    }

    @Test
    @DisplayName("Account Ledger: reserveBuyMargin throws InsufficientFundsException when funds are insufficient")
    void testInsufficientFundsThrows() {
        accountService.createAccount(101L);
        // Requires more than 10,000,000 USDT
        assertThrows(InsufficientFundsException.class, () ->
                accountService.reserveBuyMargin(101L, 1L, 5_000_000L, 100_000_000_000L, System.nanoTime())
        );
    }

    @Test
    @DisplayName("Account Ledger Facade: cancelOrder releases reserved margin")
    void testCancelOrderFacade() {
        accountService.createAccount(101L);
        accountService.reserveBuyMargin(101L, 1L, 5_000_000L, 4_000_000_000L, System.nanoTime());
        accountService.cancelOrder(101L, 1L);

        AccountBalanceResponse balance = accountService.getBalance(101L);
        assertEquals("10000000.00", balance.availableUsdt());
        assertEquals("0.00", balance.reservedUsdt());
        assertNull(accountService.getActiveOrder(101L, 1L));
    }

    @Test
    @DisplayName("Account Concurrency: 10 concurrent threads competing for limited balance never overdraw (CAS safety)")
    void testConcurrentOverdrawProtection() throws InterruptedException {
        Account account = new Account(1_000L, 0L);

        int threadCount = 10;
        // each wants 200 USDT margin (only 5 can succeed out of 10)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long orderId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 200 cents * 100,000,000 sats / 100,000,000 = 200 cents margin
                    account.reserveBuyMargin(orderId, 200L, 100_000_000L, System.nanoTime());
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException expected) {
                    // Expected for threads 6-10 when balance is exhausted!
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
        assertEquals(0L, account.getAvailableUsdt(), "Available balance must not be negative");
        assertEquals(1_000L, account.getReservedUsdt(), "Reserved USDT must equal exactly 1,000");
        assertEquals(5, account.getActiveOrders().size());
    }

    @Test
    @DisplayName("Partial Fill & Settle: Partial match maintains active order until fully filled")
    void testPartialFillAndSettlement() {
        accountService.createAccount(101L);
        // Buyer places order for 10 BTC (1,000,000,000 sats) @ 50,000 USDT (5,000,000 cents) = 500,000 USDT (50,000,000 cents) margin
        accountService.reserveBuyMargin(101L, 1L, 5_000_000L, 1_000_000_000L, System.nanoTime());

        Account account = accountService.getAccount(101L);
        assertEquals(50_000_000L, account.getReservedUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_USDT - 50_000_000L, account.getAvailableUsdt());
        assertEquals(1, accountService.getActiveOrders(101L).size());

        // Partial match 1: 3 BTC (300,000,000 sats) @ 50,000 USDT = 150,000 USDT (15,000,000 cents) cost
        accountService.settleBuyTrade(101L, 1L, 15_000_000L, 300_000_000L);

        // Order should STILL be active with 7 BTC (700,000,000 sats) remaining
        assertNotNull(accountService.getActiveOrder(101L, 1L));
        assertEquals(1, accountService.getActiveOrders(101L).size());
        assertEquals(700_000_000L, accountService.getActiveOrders(101L).iterator().next().getRemainingQuantity());
        assertEquals(35_000_000L, account.getReservedUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 300_000_000L, account.getAvailableBtc());

        // Partial match 2: 7 BTC (700,000,000 sats) @ 50,000 USDT = 350,000 USDT (35,000,000 cents) cost (fully filled)
        accountService.settleBuyTrade(101L, 1L, 35_000_000L, 700_000_000L);

        // Order should now be completely removed from active orders
        assertNull(accountService.getActiveOrder(101L, 1L));
        assertEquals(0, accountService.getActiveOrders(101L).size());
        assertEquals(0L, account.getReservedUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 1_000_000_000L, account.getAvailableBtc());
    }

    @Test
    @DisplayName("Partial Fill Then Cancel: Cancel releases remaining margin after partial fill")
    void testPartialFillThenCancel() {
        accountService.createAccount(101L);
        // Buyer places order for 10 BTC (1,000,000,000 sats) @ 50,000 USDT (5,000,000 cents) = 500,000 USDT (50,000,000 cents) margin
        accountService.reserveBuyMargin(101L, 1L, 5_000_000L, 1_000_000_000L, System.nanoTime());

        // Partial match: 4 BTC (400,000,000 sats) filled, cost 200,000 USDT (20,000,000 cents)
        accountService.settleBuyTrade(101L, 1L, 20_000_000L, 400_000_000L);

        // Cancel remaining 6 BTC
        accountService.cancelOrder(101L, 1L);

        Account account = accountService.getAccount(101L);
        assertNull(accountService.getActiveOrder(101L, 1L));
        assertEquals(0L, account.getReservedUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_USDT - 20_000_000L, account.getAvailableUsdt());
        assertEquals(AccountService.DEFAULT_INITIAL_BTC + 400_000_000L, account.getAvailableBtc());
    }

    @Test
    @DisplayName("Get Active Order By ID: O(1) single-order lookup and null check")
    void testGetActiveOrderById() {
        accountService.createAccount(101L);
        accountService.reserveBuyMargin(101L, 42L, 5_000_000L, 500_000_000L, System.nanoTime());

        var order = accountService.getActiveOrder(101L, 42L);
        assertNotNull(order);
        assertEquals(42L, order.getOrderId());
        assertEquals(500_000_000L, order.getInitialQuantity());
        assertEquals(500_000_000L, order.getRemainingQuantity());

        assertNull(accountService.getActiveOrder(101L, 999L), "Non-existent order should return null");

        accountService.cancelOrder(101L, 42L);
        assertNull(accountService.getActiveOrder(101L, 42L), "Cancelled order should no longer be active");
    }
}