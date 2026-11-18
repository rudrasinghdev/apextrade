package com.rudra.apextrade.bot;

import com.rudra.apextrade.common.domain.Side;

/**
 * Multi-level pyramid market maker bot.
 * Quotes bids and asks around an anchor price and cancels/reseeds when the market drifts.
 */
public class MarketMakerBot implements Runnable {

    private final ExchangeHttpClient client;
    private final ExchangeHttpClient.AuthSession session;
    private final FastFairPriceGenerator priceGen;

    private final int levels;
    private final double halfSpread;
    private final double tickStep;
    private final double orderQuantity;
    private final long quoteIntervalMs;

    private final long[] activeBidOrderIds;
    private final long[] activeAskOrderIds;

    private volatile boolean running;

    public MarketMakerBot(ExchangeHttpClient client,
                          ExchangeHttpClient.AuthSession session,
                          FastFairPriceGenerator priceGen,
                          int levels,
                          double halfSpread,
                          double tickStep,
                          double orderQuantity,
                          long quoteIntervalMs) {
        this.client = client;
        this.session = session;
        this.priceGen = priceGen;
        this.levels = levels;
        this.halfSpread = halfSpread;
        this.tickStep = tickStep;
        this.orderQuantity = orderQuantity;
        this.quoteIntervalMs = quoteIntervalMs;

        this.activeBidOrderIds = new long[levels];
        this.activeAskOrderIds = new long[levels];
        this.running = false;
    }

    public void seedQuotes(double anchorPrice) {
        for (int lvl = 0; lvl < levels; lvl++) {
            double bidPrice = anchorPrice - halfSpread - (lvl * tickStep);
            double askPrice = anchorPrice + halfSpread + (lvl * tickStep);

            if (bidPrice > 0) {
                try {
                    long bidId = client.placeLimitOrder(session.token(), Side.BUY, bidPrice, orderQuantity);
                    activeBidOrderIds[lvl] = bidId;
                    System.out.printf("[Maker #%d] Placed BID #%d @ $%.2f (%.4f BTC)%n", session.userId(), bidId, bidPrice, orderQuantity);
                } catch (Exception e) {
                    System.err.println("[Maker] Failed to place bid: " + e.getMessage());
                }
            }

            try {
                long askId = client.placeLimitOrder(session.token(), Side.SELL, askPrice, orderQuantity);
                activeAskOrderIds[lvl] = askId;
                System.out.printf("[Maker #%d] Placed ASK #%d @ $%.2f (%.4f BTC)%n", session.userId(), askId, askPrice, orderQuantity);
            } catch (Exception e) {
                System.err.println("[Maker] Failed to place ask: " + e.getMessage());
            }
        }
    }

    public void cancelQuotes() {
        for (int lvl = 0; lvl < levels; lvl++) {
            if (activeBidOrderIds[lvl] != 0L) {
                client.cancelOrder(session.token(), activeBidOrderIds[lvl]);
                activeBidOrderIds[lvl] = 0L;
            }
            if (activeAskOrderIds[lvl] != 0L) {
                client.cancelOrder(session.token(), activeAskOrderIds[lvl]);
                activeAskOrderIds[lvl] = 0L;
            }
        }
    }

    @Override
    public void run() {
        this.running = true;
        System.out.printf("[Maker Bot] Active as Trader #%d (%s). Seeding %d-level pyramid quotes...%n",
                session.userId(), session.email(), levels);

        while (running) {
            try {
                // Get next fair anchor price in dollars (from cents)
                double anchorPrice = priceGen.nextPrice() / 100.0;

                // Refresh pyramid
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

    public void stop() {
        this.running = false;
    }
}
