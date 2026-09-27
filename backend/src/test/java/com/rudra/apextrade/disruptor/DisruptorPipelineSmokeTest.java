package com.rudra.apextrade.disruptor;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.publisher.DisruptorOrderPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DisruptorPipelineSmokeTest {

    @Autowired
    private DisruptorOrderPublisher publisher;

    @Autowired
    private OrderBook orderBook;

    @Test
    @DisplayName("End-to-End Match: Maker SELL rests, Taker BUY crosses via RingBuffer")
    void testEndToEndOrderCrossing() throws ExecutionException, InterruptedException, TimeoutException {
        long makerOrderId = 1001L;
        long makerUserId = 101L;
        long price = 6_500_000L;
        long quantity = 100_000_000L;

        CompletableFuture<List<Trade>> makerFuture = publisher.publishPlaceOrder(
                makerOrderId, makerUserId, Side.SELL, price, quantity, OrderType.LIMIT
        );

        List<Trade> makerTrades = makerFuture.get(2, TimeUnit.SECONDS);
        assertTrue(makerTrades.isEmpty(), "Resting maker order must produce zero trades");
        assertEquals(price, orderBook.getBestAsk().getPrice(), "Maker order must rest at 65,000 ask");

        long takerOrderId = 1002L;
        long takerUserId = 102L;

        CompletableFuture<List<Trade>> takerFuture = publisher.publishPlaceOrder(
                takerOrderId, takerUserId, Side.BUY, price, quantity, OrderType.LIMIT
        );

        List<Trade> takerTrades = takerFuture.get(2, TimeUnit.SECONDS);
        assertEquals(1, takerTrades.size(), "Crossing taker order must produce exactly 1 trade");

        Trade trade = takerTrades.getFirst();
        assertEquals(price, trade.price(), "Trade price must match maker resting price");
        assertEquals(quantity, trade.quantity(), "Trade quantity must match 1.0 BTC");
        assertEquals(makerOrderId, trade.makerOrderId());
        assertEquals(takerOrderId, trade.takerOrderId());

        assertNull(orderBook.getBestAsk(), "Order book ask side should be empty after full match");
        assertNull(orderBook.getBestBid(), "Order book bid side should be empty");
    }

    @Test
    @DisplayName("Cancel Order Pipeline: Order is placed and unlinked via RingBuffer")
    void testCancelOrderPipeline() throws ExecutionException, InterruptedException, TimeoutException {
        long orderId = 2001L;
        long userId = 201L;
        long price = 6_400_000L;
        long quantity = 50_000_000L;

        CompletableFuture<List<Trade>> placeFuture = publisher.publishPlaceOrder(
                orderId, userId, Side.BUY, price, quantity, OrderType.LIMIT
        );
        placeFuture.get(2, TimeUnit.SECONDS);
        assertEquals(price, orderBook.getBestBid().getPrice());

        CompletableFuture<List<Trade>> cancelFuture = publisher.publishCancelOrder(orderId, userId);
        List<Trade> cancelResult = cancelFuture.get(2, TimeUnit.SECONDS);
        assertTrue(cancelResult.isEmpty());

        assertNull(orderBook.getBestBid(), "Bid price should be null after cancellation");
    }

    @Test
    @DisplayName("Concurrent Multi-Producer Burst: 100 concurrent orders without sequence collisions")
    void testMultiProducerBurst() {
        int orderCount = 100;
        List<CompletableFuture<List<Trade>>> futures = new ArrayList<>(orderCount);

        for (int i = 0; i < orderCount; i++) {
            long orderId = 3000L + i;
            long price = 7_000_000L + (i * 100L);
            long qty = 10_000_000L;

            CompletableFuture<List<Trade>> future = publisher.publishPlaceOrder(
                    orderId, 999L, Side.SELL, price, qty, OrderType.LIMIT
            );
            futures.add(future);
        }

        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.SECONDS);
        }, "All concurrent burst orders must resolve within 5 seconds without ring buffer deadlocks");
    }
}
