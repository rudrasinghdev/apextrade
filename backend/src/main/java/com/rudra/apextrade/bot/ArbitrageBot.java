package com.rudra.apextrade.bot;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.PriceLevel;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.publisher.DisruptorOrderPublisher;

/**
 * Aggressive cross-spread taker bot (designed to run pinned to Core 2).
 * Compares fair price against top of book and fires crossing limit orders to trigger matches.
 */
public class ArbitrageBot implements Runnable {

    private final OrderBook orderBook;
    private final DisruptorOrderPublisher publisher;
    private final FastFairPriceGenerator priceGenerator;
    private final long userId;
    private final long sweepQuantity;

    private long orderSequence;
    private volatile boolean running;

    public ArbitrageBot(OrderBook orderBook,
                        DisruptorOrderPublisher publisher,
                        FastFairPriceGenerator priceGenerator,
                        long userId,
                        long sweepQuantity) {
        this.orderBook = orderBook;
        this.publisher = publisher;
        this.priceGenerator = priceGenerator;
        this.userId = userId;
        this.sweepQuantity = sweepQuantity;
        this.orderSequence = 0L;
        this.running = false;
    }

    /**
     * Unique 64-bit ID with high bit 2: (2L << 48) | seq
     * Mathematically guarantees zero collision with Maker bot (1L << 48).
     */
    private long nextOrderId() {
        return (2L << 48) | (++orderSequence);
    }

    /**
     * Evaluates current market depth against fair price and fires crossing orders if spread is crossed.
     */
    public boolean sweep() {
        long fairPrice = priceGenerator.nextPrice();
        long now = System.currentTimeMillis();

        PriceLevel bestAsk = orderBook.getBestAsk();
        PriceLevel bestBid = orderBook.getBestBid();

        // 1. Fair price is above best ask -> Market is underpriced -> Sweep the ask!
        if (bestAsk != null && fairPrice >= bestAsk.getPrice()) {
            long orderId = nextOrderId();
            publisher.publishPlaceOrder(orderId, userId, Side.BUY, bestAsk.getPrice(), sweepQuantity, OrderType.LIMIT, now);
            return true;
        }

        // 2. Fair price is below best bid -> Market is overpriced -> Sweep the bid!
        if (bestBid != null && fairPrice <= bestBid.getPrice()) {
            long orderId = nextOrderId();
            publisher.publishPlaceOrder(orderId, userId, Side.SELL, bestBid.getPrice(), sweepQuantity, OrderType.LIMIT, now);
            return true;
        }

        return false;
    }

    @Override
    public void run() {
        this.running = true;
        while (running) {
            sweep();
            Thread.onSpinWait();
        }
    }

    public void stop() {
        this.running = false;
    }
}

