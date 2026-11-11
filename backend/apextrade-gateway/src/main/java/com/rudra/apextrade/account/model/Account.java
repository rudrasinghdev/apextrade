package com.rudra.apextrade.account.model;

import com.rudra.apextrade.common.domain.Side;

import java.util.Collection;
import java.util.Collections;
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

    public boolean reserveBuyMargin(long orderId, long price, long quantity, long timestampNs) {
        long usdAmount = price * quantity;
        if (reserveFunds(availableUsd, reservedUsd, usdAmount)) {
            activeOrders.put(orderId, new ActiveOrder(orderId, Side.BUY, price, quantity, timestampNs));
            return true;
        }
        return false;
    }

    public boolean reserveSellMargin(long orderId, long price, long quantity, long timestampNs) {
        if (reserveFunds(availableBtc, reservedBtc, quantity)) {
            activeOrders.put(orderId, new ActiveOrder(orderId, Side.SELL, price, quantity, timestampNs));
            return true;
        }
        return false;
    }

    private boolean reserveFunds(AtomicLong available, AtomicLong reserved, long amount) {
        long current;
        do {
            current = available.get();
            if (current < amount) {
                return false;
            }
        } while (!available.compareAndSet(current, current - amount));

        reserved.addAndGet(amount);
        return true;
    }

    public void cancelOrder(long orderId) {
        ActiveOrder order = activeOrders.remove(orderId);
        if (order != null) {
            long remainingQty = order.getRemainingQuantity();
            if (order.getSide() == Side.BUY) {
                releaseFunds(availableUsd, reservedUsd, remainingQty * order.getPrice());
            } else {
                releaseFunds(availableBtc, reservedBtc, remainingQty);
            }
        }
    }

    private void releaseFunds(AtomicLong available, AtomicLong reserved, long amount) {
        reserved.addAndGet(-amount);
        available.addAndGet(amount);
    }

    public void settleBuyTrade(long orderId, long usdCost, long btcReceived) {
        reduceOrRemoveOrder(orderId, btcReceived);
        reservedUsd.addAndGet(-usdCost);
        availableBtc.addAndGet(btcReceived);
    }

    public void settleSellTrade(long orderId, long btcSold, long usdReceived) {
        reduceOrRemoveOrder(orderId, btcSold);
        reservedBtc.addAndGet(-btcSold);
        availableUsd.addAndGet(usdReceived);
    }

    private void reduceOrRemoveOrder(long orderId, long filledQuantity) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order != null && order.reduceQuantity(filledQuantity) <= 0) {
            activeOrders.remove(orderId);
        }
    }

    public boolean hasActiveOrder(long orderId) {
        return activeOrders.containsKey(orderId);
    }

    public ActiveOrder getActiveOrder(long orderId) {
        return activeOrders.get(orderId);
    }

    public Collection<ActiveOrder> getActiveOrders() {
        return Collections.unmodifiableCollection(activeOrders.values());
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
