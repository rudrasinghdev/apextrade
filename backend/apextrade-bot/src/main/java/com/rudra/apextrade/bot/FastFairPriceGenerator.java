package com.rudra.apextrade.bot;

import lombok.Getter;

public class FastFairPriceGenerator {

    @Getter
    private final long centerPrice;

    @Getter
    private volatile long currentPrice;

    private final long minPrice;
    private final long maxPrice;
    private final long tickStep;
    private long prngState;

    private int trendDirection = 1;
    private int trendRemaining = 0;

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

    public synchronized long nextPriceCents() {
        long x = prngState;
        x ^= (x << 13);
        x ^= (x >>> 7);
        x ^= (x << 17);
        prngState = x;

        long rand = x & 0x7FFFFFFFFFFFFFFFL;
        long delta = currentPrice - centerPrice;
        long maxDeviation = (maxPrice - minPrice) / 2;

        // Proportional mean-reversion pull (-1.0 at minPrice to +1.0 at maxPrice)
        double normDev = (double) delta / (double) maxDeviation;
        int upProb = (int) Math.round(50.0 - (normDev * 42.0));
        upProb = Math.clamp(upProb, 5, 95);

        // Regime-switching trend momentum
        if (trendRemaining <= 0) {
            trendDirection = ((rand % 100) < upProb) ? 1 : -1;
            trendRemaining = 15 + (int) ((rand >>> 8) % 35); // 15 to 50 tick trend wave
        } else {
            trendRemaining--;
            // If price reaches strong deviation (>40%), allow early mean-reversion reversal
            if (trendDirection > 0 && normDev > 0.40 && (rand % 100) > upProb) {
                trendDirection = -1;
                trendRemaining = 15 + (int) ((rand >>> 8) % 25);
            } else if (trendDirection < 0 && normDev < -0.40 && (rand % 100) < upProb) {
                trendDirection = 1;
                trendRemaining = 15 + (int) ((rand >>> 8) % 25);
            }
        }

        // Volatility step: 70% micro ($0.25), 20% medium ($0.50-$1.25), 10% macro ($2.00-$5.00)
        long roll = (rand >>> 16) % 100;
        long stepMultiplier;
        if (roll < 70) {
            stepMultiplier = 1;
        } else if (roll < 90) {
            stepMultiplier = 2 + ((rand >>> 24) % 4); // 2x to 5x ($0.50 - $1.25)
        } else {
            stepMultiplier = 8 + ((rand >>> 28) % 13); // 8x to 20x ($2.00 - $5.00)
        }

        long step = tickStep * stepMultiplier;

        if (trendDirection > 0) {
            currentPrice = Math.min(currentPrice + step, maxPrice);
        } else {
            currentPrice = Math.max(currentPrice - step, minPrice);
        }

        return currentPrice;
    }
}
