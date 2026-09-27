package com.rudra.apextrade.core;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.Order;
import com.rudra.apextrade.core.order.OrderStatus;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        Order maker1 = new Order(1L, 101L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L);
        Order maker2 = new Order(2L, 102L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L);

        engine.processOrder(maker1);
        engine.processOrder(maker2);

        Order taker = new Order(3L, 103L, Side.BUY, OrderType.LIMIT, 6_500_000L, 150_000_000L);
        List<Trade> trades = engine.processOrder(taker);

        assertEquals(2, trades.size(), "Should produce 2 trade executions across the 2 makers");

        Trade trade1 = trades.getFirst();
        assertEquals(1L, trade1.makerOrderId());
        assertEquals(100_000_000L, trade1.quantity());
        assertEquals(OrderStatus.FILLED, maker1.getStatus());

        Trade trade2 = trades.get(1);
        assertEquals(2L, trade2.makerOrderId());
        assertEquals(50_000_000L, trade2.quantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, maker2.getStatus());
        assertEquals(50_000_000L, maker2.getRemainingQuantity());

        assertEquals(6_500_000L, orderBook.getBestAsk().getPrice());
    }

    @Test
    @DisplayName("Multi-Level Sweeping: Large aggressive BUY sweeps through multiple ASK price levels")
    void testMultiLevelBookSweep() {
        engine.processOrder(new Order(1L, 101L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L));
        engine.processOrder(new Order(2L, 102L, Side.SELL, OrderType.LIMIT, 6_510_000L, 100_000_000L));
        engine.processOrder(new Order(3L, 103L, Side.SELL, OrderType.LIMIT, 6_520_000L, 100_000_000L));

        Order taker = new Order(4L, 104L, Side.BUY, OrderType.LIMIT, 6_525_000L, 250_000_000L);
        List<Trade> trades = engine.processOrder(taker);

        assertEquals(3, trades.size(), "Should sweep across 3 distinct price levels");

        assertEquals(6_500_000L, trades.get(0).price());
        assertEquals(100_000_000L, trades.get(0).quantity());

        assertEquals(6_510_000L, trades.get(1).price());
        assertEquals(100_000_000L, trades.get(1).quantity());

        assertEquals(6_520_000L, trades.get(2).price());
        assertEquals(50_000_000L, trades.get(2).quantity());

        assertEquals(6_520_000L, orderBook.getBestAsk().getPrice());
        assertNull(orderBook.getBestBid());
    }

    @Test
    @DisplayName("Cancel Order Unlink: Unlinking middle order preserves DLL continuity")
    void testCancelMiddleOrderPreservesQueue() {
        // 3 orders at exact same price
        Order order1 = new Order(1L, 101L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L);
        Order order2 = new Order(2L, 102L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L);
        Order order3 = new Order(3L, 103L, Side.SELL, OrderType.LIMIT, 6_500_000L, 100_000_000L);

        engine.processOrder(order1);
        engine.processOrder(order2);
        engine.processOrder(order3);

        boolean cancelled = engine.cancelOrder(2L);
        assertTrue(cancelled, "Order 2 must be successfully cancelled");
        assertEquals(OrderStatus.CANCELLED, order2.getStatus());

        Order taker = new Order(4L, 104L, Side.BUY, OrderType.LIMIT, 6_500_000L, 200_000_000L);
        List<Trade> trades = engine.processOrder(taker);

        assertEquals(2, trades.size());
        assertEquals(1L, trades.get(0).makerOrderId(), "First fill must be Order 1");
        assertEquals(3L, trades.get(1).makerOrderId(), "Second fill must be Order 3 (Order 2 was skipped)");

        assertNull(orderBook.getBestAsk());
    }
}
