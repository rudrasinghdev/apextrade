package com.rudra.apextrade.account.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Account {

    private final AtomicLong availableUsd;
    private final AtomicLong reservedUsd;

    private final AtomicLong availableBtc;
    private final AtomicLong reservedBtc;

    private final ConcurrentHashMap<Long, Long> activeBuyOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> activeSellOrders = new ConcurrentHashMap<>();

    public Account(long initialUsd, long initialBtc) {
        this.availableUsd = new AtomicLong(initialUsd);
        this.reservedUsd = new AtomicLong(0L);
        this.availableBtc = new AtomicLong(initialBtc);
        this.reservedBtc = new AtomicLong(0L);
    }

    public boolean placeBuyOrder(long orderId, long usdAmount) {
        if (!reserveUsd(usdAmount)) {
            return false;
        }
        if (usdAmount > 0) {
            activeBuyOrders.put(orderId, usdAmount);
        }
        return true;
    }

    public boolean placeSellOrder(long orderId, long btcAmount) {
        if (!reserveBtc(btcAmount)) {
            return false;
        }
        if (btcAmount > 0) {
            activeSellOrders.put(orderId, btcAmount);
        }
        return true;
    }

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

    private boolean reserveUsd(long amount) {
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

    private void releaseUsd(long amount) {
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

    private boolean reserveBtc(long amount) {
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

}
