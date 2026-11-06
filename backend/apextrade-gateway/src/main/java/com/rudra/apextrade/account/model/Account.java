package com.rudra.apextrade.account.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, lock-free account domain aggregate.
 * Encapsulates cash and crypto balances (via atomic CAS) and active order IDs in RAM.
 */
public class Account {

    private final long userId;

    // USD (Quote Asset)
    private final AtomicLong availableUsd;
    private final AtomicLong reservedUsd;

    // BTC (Base Asset)
    private final AtomicLong availableBtc;
    private final AtomicLong reservedBtc;

    // Active Resting Orders belonging exclusively to this account
    // Key: orderId -> Value: lockedAmount
    private final ConcurrentHashMap<Long, Long> activeBuyOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> activeSellOrders = new ConcurrentHashMap<>();

    public Account(long userId, long initialUsd, long initialBtc) {
        this.userId = userId;
        this.availableUsd = new AtomicLong(initialUsd);
        this.reservedUsd = new AtomicLong(0L);
        this.availableBtc = new AtomicLong(initialBtc);
        this.reservedBtc = new AtomicLong(0L);
    }

    // ==========================================
    // Active Order Lifecycle (Pre-Trade Escrow)
    // ==========================================

    /**
     * Atomically reserves USD margin and tracks the active BUY order ID.
     */
    public boolean placeBuyOrder(long orderId, long usdAmount) {
        if (!reserveUsd(usdAmount)) {
            return false;
        }
        if (usdAmount > 0) {
            activeBuyOrders.put(orderId, usdAmount);
        }
        return true;
    }

    /**
     * Atomically reserves BTC inventory and tracks the active SELL order ID.
     */
    public boolean placeSellOrder(long orderId, long btcAmount) {
        if (!reserveBtc(btcAmount)) {
            return false;
        }
        if (btcAmount > 0) {
            activeSellOrders.put(orderId, btcAmount);
        }
        return true;
    }

    /**
     * Cancels an active order, releasing the locked margin/inventory back to available.
     * @return true if order belonged to this account and was cancelled; false if not found.
     */
    public boolean cancelOrder(long orderId) {
        Long usdLocked = activeBuyOrders.remove(orderId);
        if (usdLocked != null) {
            releaseUsd(usdLocked);
            return true;
        }

        Long btcLocked = activeSellOrders.remove(orderId);
        if (btcLocked != null) {
            releaseBtc(btcLocked);
            return true;
        }

        return false;
    }

    public boolean hasActiveOrder(long orderId) {
        return activeBuyOrders.containsKey(orderId) || activeSellOrders.containsKey(orderId);
    }

    public Set<Long> getActiveOrderIds() {
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        ids.addAll(activeBuyOrders.keySet());
        ids.addAll(activeSellOrders.keySet());
        return ids;
    }

    // ==========================================
    // USD (Quote Currency) Atomic Operations
    // ==========================================

    public boolean reserveUsd(long amount) {
        if (amount <= 0) return true;

        long current;
        do {
            current = availableUsd.get();
            if (current < amount) {
                return false;
            }
        } while (!availableUsd.compareAndSet(current, current - amount));

        reservedUsd.addAndGet(amount);
        return true;
    }

    public void releaseUsd(long amount) {
        if (amount <= 0) return;
        reservedUsd.addAndGet(-amount);
        availableUsd.addAndGet(amount);
    }

    public void depositUsd(long amount) {
        if (amount > 0) {
            availableUsd.addAndGet(amount);
        }
    }

    public long getAvailableUsd() {
        return availableUsd.get();
    }

    public long getReservedUsd() {
        return reservedUsd.get();
    }

    public long getTotalUsd() {
        return availableUsd.get() + reservedUsd.get();
    }

    // ==========================================
    // BTC (Base Currency) Atomic Operations
    // ==========================================

    public boolean reserveBtc(long amount) {
        if (amount <= 0) return true;

        long current;
        do {
            current = availableBtc.get();
            if (current < amount) {
                return false;
            }
        } while (!availableBtc.compareAndSet(current, current - amount));

        reservedBtc.addAndGet(amount);
        return true;
    }

    public void releaseBtc(long amount) {
        if (amount <= 0) return;
        reservedBtc.addAndGet(-amount);
        availableBtc.addAndGet(amount);
    }

    public void depositBtc(long amount) {
        if (amount > 0) {
            availableBtc.addAndGet(amount);
        }
    }

    public long getAvailableBtc() {
        return availableBtc.get();
    }

    public long getReservedBtc() {
        return reservedBtc.get();
    }

    public long getTotalBtc() {
        return availableBtc.get() + reservedBtc.get();
    }

    // ==========================================
    // Trade Settlement (Phase 4 matching engine fills)
    // ==========================================

    public void settleBuyTrade(long orderId, long usdCost, long btcReceived) {
        activeBuyOrders.remove(orderId);
        reservedUsd.addAndGet(-usdCost);
        availableBtc.addAndGet(btcReceived);
    }

    public void settleSellTrade(long orderId, long btcSold, long usdReceived) {
        activeSellOrders.remove(orderId);
        reservedBtc.addAndGet(-btcSold);
        availableUsd.addAndGet(usdReceived);
    }

    public long getUserId() {
        return userId;
    }
}

