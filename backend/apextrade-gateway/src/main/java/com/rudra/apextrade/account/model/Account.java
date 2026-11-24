package com.rudra.apextrade.account.model;

import com.rudra.apextrade.account.exception.InsufficientFundsException;
import com.rudra.apextrade.common.domain.Side;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Account {

    private final AtomicLong availableUsdt;
    private final AtomicLong reservedUsdt;

    private final AtomicLong availableBtc;
    private final AtomicLong reservedBtc;
    private static final long SATOSHI_PER_BTC = 100_000_000L;

    private final ConcurrentHashMap<Long, ActiveOrder> activeOrders = new ConcurrentHashMap<>();

    public Account(long initialUsdt, long initialBtc) {
        this.availableUsdt = new AtomicLong(initialUsdt);
        this.reservedUsdt = new AtomicLong(0L);
        this.availableBtc = new AtomicLong(initialBtc);
        this.reservedBtc = new AtomicLong(0L);
    }

    public void reserveBuyMargin(long orderId, long price, long quantity, long timestampNs) {
        long requiredUsdt = Math.ceilDiv(Math.multiplyExact(price, quantity), SATOSHI_PER_BTC);
        if (!reserveFunds(availableUsdt, reservedUsdt, requiredUsdt)) {
            throw new InsufficientFundsException(
                    "Insufficient USDT balance: required " + requiredUsdt + " cents to place BUY order."
            );
        }
        activeOrders.put(orderId, new ActiveOrder(orderId, Side.BUY, price, quantity, timestampNs));
    }
    public void reserveSellMargin(long orderId, long price, long quantity, long timestampNs) {
        if (!reserveFunds(availableBtc, reservedBtc, quantity)) {
            throw new InsufficientFundsException(
                    "Insufficient BTC balance: required " + quantity + " Satoshi to place SELL order."
            );
        }
        activeOrders.put(orderId, new ActiveOrder(orderId, Side.SELL, price, quantity, timestampNs));
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

    public void settleBuyTrade(long orderId, long usdCost, long btcReceived) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order == null) {
            return;
        }

        long reservedForThisFill = Math.ceilDiv(Math.multiplyExact(order.getPrice(), btcReceived), SATOSHI_PER_BTC);
        long priceImprovement = reservedForThisFill - usdCost;
        if (priceImprovement > 0) {
            reservedUsdt.addAndGet(-priceImprovement);
            availableUsdt.addAndGet(priceImprovement);
        }

        reduceOrRemoveOrder(orderId, btcReceived);
        reservedUsdt.addAndGet(-usdCost);
        availableBtc.addAndGet(btcReceived);
    }

    public void settleSellTrade(long orderId, long btcSold, long usdReceived) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order == null) {
            return;
        }

        reduceOrRemoveOrder(orderId, btcSold);
        reservedBtc.addAndGet(-btcSold);
        availableUsdt.addAndGet(usdReceived);
    }

    private void reduceOrRemoveOrder(long orderId, long filledQuantity) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order != null && order.reduceQuantity(filledQuantity) <= 0) {
            activeOrders.remove(orderId);
        }
    }

    public void cancelOrder(long orderId) {
        ActiveOrder order = activeOrders.get(orderId);
        if (order != null) {
            cancelOrder(orderId, order.getRemainingQuantity());
        }
    }

    public void cancelOrder(long orderId, long unfilledQuantity) {
        ActiveOrder order = activeOrders.remove(orderId);
        if (order != null) {
            if (order.getSide() == Side.BUY) {
                long refundUsd = Math.ceilDiv(Math.multiplyExact(order.getPrice(), unfilledQuantity), SATOSHI_PER_BTC);
                releaseFunds(availableUsdt, reservedUsdt, refundUsd);
            } else {
                releaseFunds(availableBtc, reservedBtc, unfilledQuantity);
            }
        }
    }

    private void releaseFunds(AtomicLong available, AtomicLong reserved, long amount) {
        reserved.addAndGet(-amount);
        available.addAndGet(amount);
    }

    public ActiveOrder getActiveOrder(long orderId) {
        return activeOrders.get(orderId);
    }

    public Collection<ActiveOrder> getActiveOrders() {
        return Collections.unmodifiableCollection(activeOrders.values());
    }

    public long getAvailableUsdt() {
        return availableUsdt.get();
    }

    public long getReservedUsdt() {
        return reservedUsdt.get();
    }

    public long getTotalUsdt() {
        return availableUsdt.get() + reservedUsdt.get();
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
