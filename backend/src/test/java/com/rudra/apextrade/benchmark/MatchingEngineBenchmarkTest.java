package com.rudra.apextrade.benchmark;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.event.CommandType;
import com.rudra.apextrade.disruptor.event.OrderCommandEvent;
import com.rudra.apextrade.disruptor.event.OrderCommandEventFactory;
import com.rudra.apextrade.disruptor.handler.OrderCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;

public class MatchingEngineBenchmarkTest {

    private static final int BUFFER_SIZE = 65536;
    private static final int WARMUP_ORDERS = 50_000;
    private static final int BENCHMARK_ORDERS = 1_000_000;

    @Test
    @DisplayName("Benchmark: 1,000,000 unthrottled orders matching throughput")
    void runBenchmark() throws Exception {
        executeBenchmark();
    }

    public static void main(String[] args) throws Exception {
        new MatchingEngineBenchmarkTest().executeBenchmark();
    }

    private void executeBenchmark() throws InterruptedException {
        // 1. Initialize Zero-GC Core Engine Pipeline
        OrderPool orderPool = new OrderPool();
        OrderBook orderBook = new OrderBook("BTC/USDT", orderPool);
        MatchingEngine matchingEngine = new MatchingEngine(orderBook);
        OrderCommandHandler handler = new OrderCommandHandler(matchingEngine);
        OrderCommandEventFactory factory = new OrderCommandEventFactory();

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "matching-core-thread");
            thread.setDaemon(true);
            return thread;
        };

        Disruptor<OrderCommandEvent> disruptor = new Disruptor<>(
                factory,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        disruptor.handleEventsWith(handler);
        RingBuffer<OrderCommandEvent> ringBuffer = disruptor.start();

        System.out.println("===============================================================");
        System.out.println("      APEXTRADE LMAX ZERO-GC MATCHING ENGINE BENCHMARK         ");
        System.out.println("===============================================================");

        // 2. Phase 1: JIT C2 Warmup
        System.out.print("[1/3] Warming up HotSpot C2 JIT compiler (" + WARMUP_ORDERS + " orders)... ");
        for (int i = 0; i < WARMUP_ORDERS; i++) {
            publishOrder(ringBuffer, i + 1, 101L, Side.SELL, 65000_00L, 10L, OrderType.LIMIT);
            publishOrder(ringBuffer, i + 100_000, 102L, Side.BUY, 65000_00L, 10L, OrderType.LIMIT);
        }
        drainRingBuffer(ringBuffer);
        System.out.println("DONE.");

        // 3. Phase 2: Pre-Seed Book Depth (Resting maker orders from OrderPool)
        int seedLevels = 20;
        int ordersPerLevel = 500;
        int totalSeededOrders = seedLevels * ordersPerLevel; // 10,000 resting maker orders
        System.out.print("[2/3] Pre-seeding order book with " + totalSeededOrders + " resting maker orders... ");

        long makerOrderId = 500_000L;
        for (int lvl = 0; lvl < seedLevels; lvl++) {
            long price = 65001_00L + (lvl * 100L); // Asks from $65,001 to $65,020
            for (int o = 0; o < ordersPerLevel; o++) {
                publishOrder(ringBuffer, ++makerOrderId, 201L, Side.SELL, price, 1_000L, OrderType.LIMIT);
            }
        }
        drainRingBuffer(ringBuffer);
        System.out.println("DONE. (Available in OrderPool: " + orderPool.getAvailableCount() + ")");

        // 4. Phase 3: The 1,000,000 Order Blast
        System.out.println("[3/3] Blasting " + String.format("%,d", BENCHMARK_ORDERS) + " unthrottled orders through RingBuffer...");

        long takerOrderId = 2_000_000L;
        long targetSequence = 0;

        // --- START HIGH-PRECISION TIMER ---
        long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_ORDERS; i++) {
            // Aggressive Crossing BUY orders sweeping asks
            targetSequence = publishOrder(ringBuffer, ++takerOrderId, 301L, Side.BUY, 65050_00L, 1L, OrderType.LIMIT);
        }

        // Wait until Core 1 finishes processing the final order
        while (ringBuffer.getMinimumGatingSequence() < targetSequence) {
            Thread.onSpinWait();
        }

        // --- STOP HIGH-PRECISION TIMER ---
        long endTime = System.nanoTime();

        // 5. Calculate Metrics
        long elapsedNanos = endTime - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double ordersPerSecond = BENCHMARK_ORDERS / elapsedSeconds;
        double avgLatencyNanos = (double) elapsedNanos / BENCHMARK_ORDERS;

        System.out.println("===============================================================");
        System.out.printf("  Total Orders Processed : %,d%n", BENCHMARK_ORDERS);
        System.out.printf("  Total Elapsed Time     : %.2f ms (%.4f seconds)%n", elapsedNanos / 1_000_000.0, elapsedSeconds);
        System.out.printf("  Throughput (TPS)       : %,.0f Orders/Second%n", ordersPerSecond);
        System.out.printf("  Avg Processing Time    : %.1f nanoseconds / order%n", avgLatencyNanos);
        System.out.printf("  Remaining Pool Orders  : %,d / %,d%n", orderPool.getAvailableCount(), orderPool.getCapacity());
        System.out.println("===============================================================");

        disruptor.shutdown();
    }

    private static long publishOrder(RingBuffer<OrderCommandEvent> ringBuffer,
                                     long orderId, long userId, Side side,
                                     long price, long quantity, OrderType orderType) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.set(CommandType.PLACE_ORDER, userId, orderId, side, price, quantity, orderType, 0L);
        } finally {
            ringBuffer.publish(sequence);
        }
        return sequence;
    }

    private static void drainRingBuffer(RingBuffer<OrderCommandEvent> ringBuffer) {
        long cursor = ringBuffer.getCursor();
        while (ringBuffer.getMinimumGatingSequence() < cursor) {
            Thread.onSpinWait();
        }
    }
}
