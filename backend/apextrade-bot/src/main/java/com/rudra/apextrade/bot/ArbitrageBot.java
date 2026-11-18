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
                double fairPrice = priceGen.nextPrice() / 100.0;
                Side side = toggleSide ? Side.BUY : Side.SELL;
                toggleSide = !toggleSide;

                long orderId = client.placeLimitOrder(session.token(), side, fairPrice, sweepQuantity);
                if (orderId > 0) {
                    System.out.printf("[Taker #%d] Cross Order #%d FIRED [%s %.4f BTC @ $%.2f]%n",
                            session.userId(), orderId, side, sweepQuantity, fairPrice);
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
