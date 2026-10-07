package com.rudra.apextrade.core;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private OrderBook orderBook;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("BTC/USDT");
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
}