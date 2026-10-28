package com.rudra.apextrade.bot;

import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.publisher.OrderCommandPublisher;
import lombok.Getter;

public class MarketMakerBot implements Runnable {

    private final MatchingEngine engine;
    private final OrderCommandPublisher publisher;
    private final long userId;

    private final int levels;
    private final int ordersPerLevel;
    private final long halfSpread;
    private final long tickStep;
    private final long orderQuantity;
    private final long driftThreshold;

    private final long[][] activeBidOrderIds;
    private final long[][] activeAskOrderIds;

    private long orderSequence = 0L;

    @Getter
    private long currentAnchorPrice;
    @Getter
    private boolean activeQuotes;
    private volatile boolean running;

    public MarketMakerBot(MatchingEngine engine,
                          OrderCommandPublisher publisher,
                          long userId,
                          int levels,
                          int ordersPerLevel,
                          long halfSpread,
                          long tickStep,
                          long orderQuantity,
                          long driftThreshold) {
        this.engine = engine;
        this.publisher = publisher;
        this.userId = userId;
        this.levels = levels;
        this.ordersPerLevel = ordersPerLevel;
        this.halfSpread = halfSpread;
        this.tickStep = tickStep;
        this.orderQuantity = orderQuantity;
        this.driftThreshold = driftThreshold;

        this.activeBidOrderIds = new long[levels][ordersPerLevel];
        this.activeAskOrderIds = new long[levels][ordersPerLevel];

        this.currentAnchorPrice = 0L;
        this.activeQuotes = false;
        this.running = false;
    }

    private long nextOrderId() {
        return (1L << 48) | (++orderSequence);
    }

    public void seedAtBoot() {
        long ltp = engine.getLastTradedPrice();
        if (ltp <= 0) {
            return;
        }
        placePyramid(ltp);
    }

    public void quote() {
        long ltp = engine.getLastTradedPrice();
        if (ltp <= 0) {
            return;
        }

        if (!activeQuotes) {
            placePyramid(ltp);
            return;
        }

        if (Math.abs(ltp - currentAnchorPrice) >= driftThreshold) {
            cancelExistingQuotes();
            placePyramid(ltp);
        }
    }

    private void placePyramid(long anchorPrice) {
        long now = System.currentTimeMillis();
        this.currentAnchorPrice = anchorPrice;

        for (int lvl = 0; lvl < levels; lvl++) {

            long askPrice = anchorPrice + halfSpread + (lvl * tickStep);
            long bidPrice = anchorPrice - halfSpread - (lvl * tickStep);

            for (int o = 0; o < ordersPerLevel; o++) {

                if (bidPrice > 0) {
                    long bidId = nextOrderId();
                    activeBidOrderIds[lvl][o] = bidId;
                    publisher.publishPlaceOrder(bidId, userId, Side.BUY, bidPrice, orderQuantity, OrderType.LIMIT, now);
                }

                long askId = nextOrderId();
                activeAskOrderIds[lvl][o] = askId;
                publisher.publishPlaceOrder(askId, userId, Side.SELL, askPrice, orderQuantity, OrderType.LIMIT, now);
            }
        }

        this.activeQuotes = true;
    }

    public void cancelExistingQuotes() {
        if (!activeQuotes) {
            return;
        }

        for (int lvl = 0; lvl < levels; lvl++) {
            for (int o = 0; o < ordersPerLevel; o++) {
                long bidId = activeBidOrderIds[lvl][o];
                if (bidId != 0L) {
                    publisher.publishCancelOrder(bidId, userId);
                    activeBidOrderIds[lvl][o] = 0L;
                }

                long askId = activeAskOrderIds[lvl][o];
                if (askId != 0L) {
                    publisher.publishCancelOrder(askId, userId);
                    activeAskOrderIds[lvl][o] = 0L;
                }
            }
        }

        this.activeQuotes = false;
    }

    @Override
    public void run() {
        this.running = true;
        seedAtBoot();

        while (running) {
            quote();
            Thread.onSpinWait();
        }
    }

    public void stop() {
        this.running = false;
    }
}

