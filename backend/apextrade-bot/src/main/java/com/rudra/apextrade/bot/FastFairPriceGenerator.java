package com.rudra.apextrade.bot;

import lombok.Getter;

public class FastFairPriceGenerator {

    @Getter
    private final long centerPrice;

    @Getter
    private long currentPrice;

    private final long minPrice;
    private final long maxPrice;
    private final long tickStep;
    private long prngState;

    public FastFairPriceGenerator(long centerPrice, long maxDeviation, long tickStep, long seed) {
        this.centerPrice = centerPrice;
        this.minPrice = centerPrice - maxDeviation;
        this.maxPrice = centerPrice + maxDeviation;
        this.tickStep = tickStep;
        this.currentPrice = centerPrice;
        this.prngState = (seed == 0L) ? 0x9E3779B97F4A7C15L : seed;
    }

    public double nextPrice() {
        return nextPriceCents() / 100.0;
    }

    public long nextPriceCents() {
        long x = prngState;
        x ^= (x << 13);
        x ^= (x >>> 7);
        x ^= (x << 17);
        prngState = x;

        long delta = currentPrice - centerPrice;
        long rand = x & 0x7FFFFFFFFFFFFFFFL;

        boolean moveUp;
        if (delta > 0) {
            moveUp = (rand % 100) < 30;
        } else if (delta < 0) {
            moveUp = (rand % 100) < 70;
        } else {
            moveUp = (rand & 1) == 0;
        }

        if (moveUp) {
            if (currentPrice + tickStep <= maxPrice) {
                currentPrice += tickStep;
            }
        } else {
            if (currentPrice - tickStep >= minPrice) {
                currentPrice -= tickStep;
            }
        }

        return currentPrice;
    }
}
