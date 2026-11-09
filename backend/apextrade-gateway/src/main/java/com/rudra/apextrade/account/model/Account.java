package com.rudra.apextrade.account.model;

import com.rudra.apextrade.common.Side;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Account {

    private final AtomicLong availableUsd;
    private final AtomicLong reservedUsd;

    private final AtomicLong availableBtc;
    private final AtomicLong reservedBtc;

    private final ConcurrentHashMap<Long, ActiveOrder> activeOrders = new ConcurrentHashMap<>();

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
            activeOrders.put(orderId, new ActiveOrder(orderId, Side.BUY, usdAmount, 1L, System.nanoTime()));
        }
        return true;
    }

    public boolean placeBuyOrder(long orderId, long price, long quantity, long timestampNs) {
        long usdAmount = price * quantity;
        if (!reserveUsd(usdAmount)) {
            return false;
        }
        activeOrders.put(orderId, new ActiveOrder(orderId, Side.BUY, price, quantity, timestampNs));
        return true;
    }

    public boolean placeSellOrder(long orderId, long btcAmount) {
        if (!reserveBtc(btcAmount)) {
            return false;
        }
        if (btcAmount > 0) {
            activeOrders.put(orderId, new ActiveOrder(orderId, Side.SELL, 0L, btcAmount, System.nanoTime()));
        }
        return true;
    }

    public boolean placeSellOrder(long orderId, long price, long quantity, long timestampNs) {
        if (!reserveBtc(quantity)) {
            return false;
        }
        activeOrders.put(orderId, new ActiveOrder(orderId, Side.SELL, price, quantity, timestampNs));
        return true;
    }

    public boolean cancelOrder(long orderId) {
        ActiveOrder order = activeOrders.remove(orderId);
        if (order != null) {
            if (order.getSide() == Side.BUY) {
                long remainingQty = order.getRemainingQuantity();
                long refundUsd = (order.getPrice() > 0) ? (remainingQty * order.getPrice()) : order.getOriginalQuantity();
                releaseUsd(refundUsd);
            } else {
                long remainingQty = order.getRemainingQuantity();
                releaseBtc(remainingQty);
            }
            return true;
        }
        return false;
    }

    public boolean hasActiveOrder(long orderId) {
        return activeOrders.containsKey(orderId);
    }

    public Set<Long> getActiveOrderIds() {
        return new HashSet<>(activeOrders.keySet());
    }

    public Collection<ActiveOrder> getActiveOrders() {
        return Collections.unmodifiableCollection(activeOrders.values());
    }

    public void settleBuyTrade(long orderId, long usdCost, long btcReceived) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order != null) {
            long remaining = order.reduceQuantity(btcReceived);
            if (remaining <= 0) {
                activeOrders.remove(orderId);
            }
        }
        reservedUsd.addAndGet(-usdCost);
        availableBtc.addAndGet(btcReceived);
    }

    public void settleSellTrade(long orderId, long btcSold, long usdReceived) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order != null) {
            long remaining = order.reduceQuantity(btcSold);
            if (remaining <= 0) {
                activeOrders.remove(orderId);
            }
        }
        reservedBtc.addAndGet(-btcSold);
        availableUsd.addAndGet(usdReceived);
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
}
