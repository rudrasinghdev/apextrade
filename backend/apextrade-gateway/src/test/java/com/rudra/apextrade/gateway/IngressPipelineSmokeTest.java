package com.rudra.apextrade.ingress;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "apextrade.runner.enabled=false")
class IngressPipelineSmokeTest {

    @Autowired
    private OrderCommandPublisher publisher;

    @Autowired
    private OrderBook orderBook;

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
    @DisplayName("End-to-End Match: Maker SELL rests, Taker BUY crosses via RingBuffer")
    void testEndToEndOrderCrossing() throws Exception {
        long price = 6_500_000L;
        long quantity = 100L;

        // 1. Maker SELL arrives via RingBuffer
        publisher.publishPlaceOrder(1001L, 101L, Side.SELL, price, quantity, OrderType.LIMIT, 1000L);

        // Wait for matching thread to place it in the book
        waitUntil(() -> {
            assertNotNull(orderBook.getBestAsk());
            assertEquals(price, orderBook.getBestAsk().getPrice());
            assertEquals(quantity, orderBook.getBestAsk().getTotalVolume());
        }, 1000);

        // 2. Taker BUY arrives via RingBuffer and crosses
        publisher.publishPlaceOrder(1002L, 102L, Side.BUY, price, quantity, OrderType.LIMIT, 1001L);

        // Wait for matching thread to cross them
        waitUntil(() -> {
            assertNull(orderBook.getBestAsk(), "Ask side should be empty after full match");
            assertNull(orderBook.getBestBid(), "Bid side should be empty after full match");
        }, 1000);
    }

    @Test
    @DisplayName("Cancel Order Pipeline: Order is placed and cancelled via RingBuffer")
    void testCancelOrderPipeline() throws Exception {
        long orderId = 2001L;
        long price = 6_400_000L;
        long quantity = 50L;

        publisher.publishPlaceOrder(orderId, 201L, Side.BUY, price, quantity, OrderType.LIMIT, 2000L);

        waitUntil(() -> {
            assertNotNull(orderBook.getBestBid());
            assertEquals(price, orderBook.getBestBid().getPrice());
        }, 1000);

        publisher.publishCancelOrder(orderId, 201L);

        waitUntil(() -> {
            assertNull(orderBook.getBestBid(), "Bid should be null after cancellation");
        }, 1000);
    }
}
