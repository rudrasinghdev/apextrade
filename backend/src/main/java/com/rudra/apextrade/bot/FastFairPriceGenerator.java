package com.rudra.apextrade.bot;

import lombok.Getter;

/**
 * High-performance, zero-allocation bounded mean-reverting fair price generator.
 * Uses a 2ns bitwise XORShift64 PRNG to walk price within [center - maxDev, center + maxDev].
 */
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

    /**
     * Generates next fair price in ~2-3ns without any heap allocations.
     * Biases step direction toward centerPrice to enforce bounded mean-reversion.
     */
    public long nextPrice() {
        // 1. XORShift64 bitwise step
        long x = prngState;
        x ^= (x << 13);
        x ^= (x >>> 7);
        x ^= (x << 17);
        prngState = x;

        // 2. Mean-reverting direction logic
        long delta = currentPrice - centerPrice;
        long rand = x & 0x7FFFFFFFFFFFFFFFL; // Positive long

        boolean moveUp;
        if (delta > 0) {
            // Above center: 70% probability to pull down, 30% to push up
            moveUp = (rand % 100) < 30;
        } else if (delta < 0) {
            // Below center: 70% probability to pull up, 30% to push down
            moveUp = (rand % 100) < 70;
        } else {
            // At center: 50 / 50
            moveUp = (rand & 1) == 0;
        }

        // 3. Apply step bounded by corridor
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
