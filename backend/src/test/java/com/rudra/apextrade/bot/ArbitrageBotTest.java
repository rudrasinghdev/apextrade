package com.rudra.apextrade.bot;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.publisher.OrderCommandPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "apextrade.runner.enabled=false")
class ArbitrageBotTest {

    @Autowired
    private OrderCommandPublisher publisher;

    @Autowired
    private OrderBook orderBook;

    @Autowired
    private MatchingEngine matchingEngine;

    private void waitUntil(Runnable assertion, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastError = e;
                TimeUnit.MILLISECONDS.sleep(5);
            }
        }
        if (lastError != null) throw lastError;
    }

    @Test
    @DisplayName("Taker BUY sweeps resting Maker Ask when Fair Price is above Best Ask")
    void testTakerSweepsAskWhenFairPriceRises() throws Exception {
        long askPrice = 50_010L;
        long qty = 100L;

        // 1. Seed resting Maker Ask
        publisher.publishPlaceOrder(101L, 1L, Side.SELL, askPrice, qty, OrderType.LIMIT, System.currentTimeMillis());
        waitUntil(() -> {
            assertNotNull(orderBook.getBestAsk());
            assertEquals(askPrice, orderBook.getBestAsk().getPrice());
        }, 1000);

        // 2. Create PriceGenerator producing 50,020 (above best ask 50,010)
        FastFairPriceGenerator highPriceGen = new FastFairPriceGenerator(50_020L, 0L, 0L, 1L);
        ArbitrageBot taker = new ArbitrageBot(orderBook, publisher, highPriceGen, 2L, qty);

        // 3. Taker sweeps
        boolean swept = taker.sweep();
        assertTrue(swept, "Taker should identify crossing opportunity and fire sweep order");

        // 4. Verify match occurred and LTP updated
        waitUntil(() -> {
            assertEquals(askPrice, matchingEngine.getLastTradedPrice(), "LTP should update to matched price");
            assertNull(orderBook.getBestAsk(), "Ask should be consumed by crossing sweep");
        }, 1000);
    }

    @Test
    @DisplayName("Taker SELL sweeps resting Maker Bid when Fair Price is below Best Bid")
    void testTakerSweepsBidWhenFairPriceDrops() throws Exception {
        long bidPrice = 49_990L;
        long qty = 100L;

        // 1. Seed resting Maker Bid
        publisher.publishPlaceOrder(201L, 1L, Side.BUY, bidPrice, qty, OrderType.LIMIT, System.currentTimeMillis());
        waitUntil(() -> {
            assertNotNull(orderBook.getBestBid());
            assertEquals(bidPrice, orderBook.getBestBid().getPrice());
        }, 1000);

        // 2. Create PriceGenerator producing 49,980 (below best bid 49,990)
        FastFairPriceGenerator lowPriceGen = new FastFairPriceGenerator(49_980L, 0L, 0L, 1L);
        ArbitrageBot taker = new ArbitrageBot(orderBook, publisher, lowPriceGen, 2L, qty);

        // 3. Taker sweeps
        boolean swept = taker.sweep();
        assertTrue(swept, "Taker should identify crossing opportunity and fire sweep order");

        // 4. Verify match occurred and LTP updated
        waitUntil(() -> {
            assertEquals(bidPrice, matchingEngine.getLastTradedPrice(), "LTP should update to matched price");
            assertNull(orderBook.getBestBid(), "Bid should be consumed by crossing sweep");
        }, 1000);
    }

    @Test
    @DisplayName("Taker does NOT sweep when Fair Price is within spread")
    void testNoSweepWhenInsideSpread() throws Exception {
        long bidPrice = 49_990L;
        long askPrice = 50_010L;

        publisher.publishPlaceOrder(301L, 1L, Side.BUY, bidPrice, 100L, OrderType.LIMIT, System.currentTimeMillis());
        publisher.publishPlaceOrder(302L, 1L, Side.SELL, askPrice, 100L, OrderType.LIMIT, System.currentTimeMillis());

        waitUntil(() -> {
            assertNotNull(orderBook.getBestBid());
            assertNotNull(orderBook.getBestAsk());
        }, 1000);

        // Fair price is 50,000 (right between 49,990 and 50,010)
        FastFairPriceGenerator midPriceGen = new FastFairPriceGenerator(50_000L, 0L, 0L, 1L);
        ArbitrageBot taker = new ArbitrageBot(orderBook, publisher, midPriceGen, 2L, 100L);

        boolean swept = taker.sweep();
        assertFalse(swept, "Taker should not sweep when fair price is within bid-ask spread");

        // Clean up
        publisher.publishCancelOrder(301L, 1L);
        publisher.publishCancelOrder(302L, 1L);
    }
}
