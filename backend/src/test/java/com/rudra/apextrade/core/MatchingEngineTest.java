package com.rudra.apextrade.core;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private OrderPool orderPool;
    private OrderBook orderBook;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        orderPool = new OrderPool();
        orderBook = new OrderBook("BTC/USDT", orderPool);
        engine = new MatchingEngine(orderBook);
    }

    @Test
    @DisplayName("FIFO Priority: Earliest maker at same price level matches first")
    void testPriceTimePriorityFIFO() {
        // Maker 1 & Maker 2 at 65,000
        engine.processOrder(101L, 1L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1000L);
        engine.processOrder(102L, 2L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1001L);

        assertEquals(6_500_000L, orderBook.getBestAsk().getPrice());
        assertEquals(200L, orderBook.getBestAsk().getTotalVolume());
        assertEquals(2, orderBook.getBestAsk().getOrderCount());

        // Taker BUY for 150 (sweeps Maker 1 completely, fills Maker 2 partially)
        engine.processOrder(103L, 3L, Side.BUY, 6_500_000L, 150L, OrderType.LIMIT, 1002L);

        // Verification: Maker 1 gone, Maker 2 has 50 left
        assertEquals(6_500_000L, orderBook.getBestAsk().getPrice());
        assertEquals(50L, orderBook.getBestAsk().getTotalVolume());
        assertEquals(1, orderBook.getBestAsk().getOrderCount());
        assertEquals(2L, orderBook.getBestAsk().getFirstOrder().getOrderId());
        assertEquals(50L, orderBook.getBestAsk().getFirstOrder().getRemainingQuantity());
        assertFalse(orderBook.hasBids(), "Taker order was completely filled, must not rest in bid book");
    }

    @Test
    @DisplayName("Multi-Level Sweeping: Large aggressive BUY sweeps through multiple ASK price levels")
    void testMultiLevelBookSweep() {
        engine.processOrder(101L, 1L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1000L);
        engine.processOrder(102L, 2L, Side.SELL, 6_510_000L, 100L, OrderType.LIMIT, 1001L);
        engine.processOrder(103L, 3L, Side.SELL, 6_520_000L, 100L, OrderType.LIMIT, 1002L);

        // Taker BUY for 250 at 65,250 (sweeps 65k, 65.1k, and 50 of 65.2k)
        engine.processOrder(104L, 4L, Side.BUY, 6_525_000L, 250L, OrderType.LIMIT, 1003L);

        // Verification: only 50 left at 65,200
        assertEquals(6_520_000L, orderBook.getBestAsk().getPrice());
        assertEquals(50L, orderBook.getBestAsk().getTotalVolume());
        assertFalse(orderBook.hasBids());
    }

    @Test
    @DisplayName("Cancel Order: Middle order unlinking preserves doubly-linked list continuity")
    void testCancelMiddleOrderPreservesQueue() {
        engine.processOrder(101L, 1L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1000L);
        engine.processOrder(102L, 2L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1001L);
        engine.processOrder(103L, 3L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, 1002L);

        boolean cancelled = engine.cancelOrder(2L);
        assertTrue(cancelled, "Order 2 must be successfully cancelled");
        assertEquals(200L, orderBook.getBestAsk().getTotalVolume());
        assertEquals(2, orderBook.getBestAsk().getOrderCount());

        // Taker sweeps remaining 200 (fills Order 1 and Order 3)
        engine.processOrder(104L, 4L, Side.BUY, 6_500_000L, 200L, OrderType.LIMIT, 1003L);
        assertNull(orderBook.getBestAsk(), "Ask book must be completely empty");
    }

    @Test
    @DisplayName("Edge Case: Partial Fill with remaining quantity resting in book")
    void testPartialFillRemainingQuantityRests() {
        // Maker ASK of 40 at 65,000
        engine.processOrder(101L, 1L, Side.SELL, 6_500_000L, 40L, OrderType.LIMIT, 1000L);

        // Incoming BUY Limit of 100 at 65,000 (fills 40, remaining 60 must rest as BID)
        engine.processOrder(102L, 2L, Side.BUY, 6_500_000L, 100L, OrderType.LIMIT, 1001L);

        // Verification
        assertNull(orderBook.getBestAsk(), "Ask book must be empty after 40 units filled");
        assertTrue(orderBook.hasBids(), "Bid book must contain remaining 60 units");
        assertEquals(6_500_000L, orderBook.getBestBid().getPrice());
        assertEquals(60L, orderBook.getBestBid().getTotalVolume());
        assertEquals(1, orderBook.getBestBid().getOrderCount());
        assertEquals(2L, orderBook.getBestBid().getFirstOrder().getOrderId());
        assertEquals(60L, orderBook.getBestBid().getFirstOrder().getRemainingQuantity());
    }

    @Test
    @DisplayName("Edge Case: OrderPool Zero-Leak Recycling on Fills and Cancels")
    void testOrderPoolRecycling() {
        int initialAvailable = orderPool.getAvailableCount();
        assertEquals(100_000, initialAvailable);

        // 1. Place 5 resting maker orders -> borrows 5
        for (long i = 1; i <= 5; i++) {
            engine.processOrder(100L + i, i, Side.SELL, 6_500_000L + (i * 100), 10L, OrderType.LIMIT, 1000L + i);
        }
        assertEquals(initialAvailable - 5, orderPool.getAvailableCount(), "Pool should decrease by 5");

        // 2. Cancel 2 orders -> releases 2 back to pool
        engine.cancelOrder(1L);
        engine.cancelOrder(2L);
        assertEquals(initialAvailable - 3, orderPool.getAvailableCount(), "Pool should recover 2 orders");

        // 3. Taker sweeps remaining 3 orders completely -> releases 3 back to pool
        engine.processOrder(200L, 99L, Side.BUY, 6_600_000L, 30L, OrderType.LIMIT, 2000L);

        // 4. Verify 100% pool recovery
        assertEquals(initialAvailable, orderPool.getAvailableCount(), "All orders must be recycled back to OrderPool");
        assertNull(orderBook.getBestAsk(), "Book must be empty");
    }

    @Test
    @DisplayName("Edge Case: OrderPool Exhaustion Graceful Handling (No NullPointerException)")
    void testOrderPoolExhaustionHandling() {
        // Setup isolated book with a tiny capacity pool of 2
        OrderPool tinyPool = new OrderPool(2);
        OrderBook tinyBook = new OrderBook("ETH/USDT", tinyPool);
        MatchingEngine tinyEngine = new MatchingEngine(tinyBook);

        // Fill pool capacity
        tinyEngine.processOrder(1L, 1L, Side.SELL, 3000_00L, 10L, OrderType.LIMIT, 1000L);
        tinyEngine.processOrder(2L, 2L, Side.SELL, 3001_00L, 10L, OrderType.LIMIT, 1001L);
        assertEquals(0, tinyPool.getAvailableCount());

        // 3rd order attempts to rest when pool is exhausted
        assertDoesNotThrow(() -> {
            tinyEngine.processOrder(3L, 3L, Side.SELL, 3002_00L, 10L, OrderType.LIMIT, 1002L);
        }, "Pool exhaustion must not throw NullPointerException");

        assertEquals(2, tinyBook.hasAsks() ? 2 : 0);
    }
}
