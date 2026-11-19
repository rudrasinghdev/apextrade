package com.rudra.apextrade.bot;

import com.rudra.apextrade.common.domain.Side;

/**
 * Arbitrage / Taker bot that periodically crosses the spread.
 * Fires crossing limit orders to trigger matches against resting liquidity,
 * generating public trade fills and driving real-time tape volume.
 */
public class ArbitrageBot implements Runnable {

    private final ExchangeHttpClient client;
    private final ExchangeHttpClient.AuthSession session;
    private final FastFairPriceGenerator priceGen;
    private final double sweepQuantity;
    private final long sweepIntervalMs;

    private volatile boolean running;
    private boolean toggleSide = true;

    public ArbitrageBot(ExchangeHttpClient client,
                        ExchangeHttpClient.AuthSession session,
                        FastFairPriceGenerator priceGen,
                        double sweepQuantity,
                        long sweepIntervalMs) {
        this.client = client;
        this.session = session;
        this.priceGen = priceGen;
        this.sweepQuantity = sweepQuantity;
        this.sweepIntervalMs = sweepIntervalMs;
        this.running = false;
    }

    @Override
    public void run() {
        this.running = true;
        System.out.printf("[Taker Bot] Active as Trader #%d (%s). Ready to cross spread...%n",
                session.userId(), session.email());

        while (running) {
            try {
                Thread.sleep(sweepIntervalMs);

                // Sample fair price in dollars
                double fairPrice = priceGen.nextPrice();
                Side side = toggleSide ? Side.BUY : Side.SELL;
                toggleSide = !toggleSide;

                // Aggressively cross the spread (maker half-spread is $0.50, ±$1.25 crosses top-of-book)
                double sweepPrice = (side == Side.BUY) ? (fairPrice + 1.25) : Math.max(1.0, fairPrice - 1.25);

                long orderId = client.placeLimitOrder(session.token(), side, sweepPrice, sweepQuantity);
                if (orderId > 0) {
                    System.out.printf("[Taker #%d] Cross Order #%d FIRED [%s %.4f BTC @ $%.2f (market anchor $%.2f)]%n",
                            session.userId(), orderId, side, sweepQuantity, sweepPrice, fairPrice);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Taker Error] " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }

        System.out.println("[Taker Bot] Stopped.");
    }

    public void stop() {
        this.running = false;
    }
}
