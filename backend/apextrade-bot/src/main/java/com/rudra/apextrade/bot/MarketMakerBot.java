package com.rudra.apextrade.bot;

import com.rudra.apextrade.common.domain.Side;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MarketMakerBot implements Runnable {

    private final ExchangeHttpClient client;
    private final ExchangeHttpClient.AuthSession session;
    private final FastFairPriceGenerator priceGen;

    private final int levels;
    private final int slicesPerLevel;
    private final double halfSpread;
    private final double tickStep;
    private final double totalQuantityPerLevel;
    private final long quoteIntervalMs;

    private final long[] activeBidOrderIds;
    private final long[] activeAskOrderIds;

    private volatile boolean running;

    public MarketMakerBot(ExchangeHttpClient client,
                          ExchangeHttpClient.AuthSession session,
                          FastFairPriceGenerator priceGen,
                          int levels,
                          int slicesPerLevel,
                          double halfSpread,
                          double tickStep,
                          double totalQuantityPerLevel,
                          long quoteIntervalMs) {
        this.client = client;
        this.session = session;
        this.priceGen = priceGen;
        this.levels = levels;
        this.slicesPerLevel = Math.max(1, slicesPerLevel);
        this.halfSpread = halfSpread;
        this.tickStep = tickStep;
        this.totalQuantityPerLevel = totalQuantityPerLevel;
        this.quoteIntervalMs = quoteIntervalMs;

        int totalOrders = levels * this.slicesPerLevel;
        this.activeBidOrderIds = new long[totalOrders];
        this.activeAskOrderIds = new long[totalOrders];
        this.running = false;
    }

    @Override
    public void run() {
        this.running = true;
        System.out.printf("[Maker Bot] Active as Trader #%d (%s). Seeding %d levels (%d slices/level = %d orders/side)...%n",
                session.userId(), session.email(), levels, slicesPerLevel, levels * slicesPerLevel);

        while (running) {
            try {
                double anchorPrice = priceGen.nextPrice();

                cancelQuotes();
                seedQuotes(anchorPrice);

                Thread.sleep(quoteIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Maker Error] " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }

        cancelQuotes();
        System.out.println("[Maker Bot] Stopped.");
    }

    private void seedQuotes(double anchorPrice) {
        AtomicInteger placedBids = new AtomicInteger(0);
        AtomicInteger placedAsks = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int lvl = 0; lvl < levels; lvl++) {
                final double bidPrice = anchorPrice - halfSpread - (lvl * tickStep);
                final double askPrice = anchorPrice + halfSpread + (lvl * tickStep);

                for (int s = 0; s < slicesPerLevel; s++) {
                    final int idx = lvl * slicesPerLevel + s;
                    final double sliceQty = calculateSliceQuantity(s, slicesPerLevel, totalQuantityPerLevel);

                    if (bidPrice > 0) {
                        executor.submit(() -> {
                            try {
                                long bidId = client.placeLimitOrder(session.token(), Side.BUY, bidPrice, sliceQty);
                                activeBidOrderIds[idx] = bidId;
                                placedBids.incrementAndGet();
                            } catch (Exception e) {
                                System.err.println("[Maker] Failed to place bid: " + e.getMessage());
                            }
                        });
                    }

                    executor.submit(() -> {
                        try {
                            long askId = client.placeLimitOrder(session.token(), Side.SELL, askPrice, sliceQty);
                            activeAskOrderIds[idx] = askId;
                            placedAsks.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("[Maker] Failed to place ask: " + e.getMessage());
                        }
                    });
                }
            }
        }

        double bestBid = anchorPrice - halfSpread;
        double deepestBid = anchorPrice - halfSpread - ((levels - 1) * tickStep);
        double bestAsk = anchorPrice + halfSpread;
        double deepestAsk = anchorPrice + halfSpread + ((levels - 1) * tickStep);
        System.out.printf("[Maker #%d] Seeded %d Bids [$%.2f -> $%.2f] & %d Asks [$%.2f -> $%.2f] (%.2f BTC depth/side)%n",
                session.userId(), placedBids.get(), bestBid, deepestBid, placedAsks.get(), bestAsk, deepestAsk, levels * totalQuantityPerLevel);
    }

    private void cancelQuotes() {
        int totalOrders = levels * slicesPerLevel;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalOrders; i++) {
                final int idx = i;
                final long bidId = activeBidOrderIds[idx];
                activeBidOrderIds[idx] = 0L;
                if (bidId != 0L) {
                    executor.submit(() -> client.cancelOrder(session.token(), bidId));
                }

                final long askId = activeAskOrderIds[idx];
                activeAskOrderIds[idx] = 0L;
                if (askId != 0L) {
                    executor.submit(() -> client.cancelOrder(session.token(), askId));
                }
            }
        }
    }

    private double calculateSliceQuantity(int sliceIndex, int totalSlices, double totalLevelQty) {
        if (totalSlices <= 1) return totalLevelQty;
        if (totalSlices == 2) {
            // Asymmetric split: 60% / 40% (e.g. 0.03 BTC and 0.02 BTC)
            return (sliceIndex == 0) ? (totalLevelQty * 0.6) : (totalLevelQty * 0.4);
        }
        return totalLevelQty / totalSlices;
    }

    public void stop() {
        this.running = false;
    }
}
