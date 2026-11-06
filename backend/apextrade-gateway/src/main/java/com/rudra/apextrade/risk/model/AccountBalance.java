package com.rudra.apextrade.risk.model;

import java.util.concurrent.atomic.AtomicLong;

public class AccountBalance {

    private final AtomicLong availableUsd;
    private final AtomicLong reservedUsd;

    private final AtomicLong availableBtc;
    private final AtomicLong reservedBtc;

    public void depositUsd(long amount) {
        if (amount > 0) {
            availableUsd.addAndGet(amount);
        }
    }

    public AccountBalance(long initialUsd, long initialBtc) {
        this.availableUsd = new AtomicLong(initialUsd);
        this.reservedUsd = new AtomicLong(0L);
        this.availableBtc = new AtomicLong(initialBtc);
        this.reservedBtc = new AtomicLong(0L);
    }

    public boolean reserveUsd(long amount) {
        if (amount <= 0) {
            return true;
        }

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
        if (amount <= 0) {
            return;
        }
        reservedUsd.addAndGet(-amount);
        availableUsd.addAndGet(amount);
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

    public void depositBtc(long amount) {
        if (amount > 0) {
            availableBtc.addAndGet(amount);
        }
    }

    public boolean reserveBtc(long amount) {
        if (amount <= 0) {
            return true;
        }

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
        if (amount <= 0) {
            return;
        }
        reservedBtc.addAndGet(-amount);
        availableBtc.addAndGet(amount);
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

    public void settleBuyTrade(long usdCost, long btcReceived) {
        reservedUsd.addAndGet(-usdCost);
        availableBtc.addAndGet(btcReceived);
    }

    public void settleSellTrade(long btcSold, long usdReceived) {
        reservedBtc.addAndGet(-btcSold);
        availableUsd.addAndGet(usdReceived);
    }
}
