package com.rudra.apextrade.core.benchmark;

import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.publisher.OrderCommandPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final OrderCommandPublisher publisher;
    private final OrderPool orderPool;

    public BenchmarkController(OrderCommandPublisher publisher, OrderPool orderPool) {
        this.publisher = publisher;
        this.orderPool = orderPool;
    }

    @PostMapping("/start")
    public ResponseEntity<BenchmarkMetrics> runBenchmark(
            @RequestParam(defaultValue = "1000000") int orders) {

        System.out.println("\n=======================================================");
        System.out.println(">>> STARTING BARE-METAL BENCHMARK: " + orders + " ORDERS <<<");
        System.out.println("=======================================================");

        long userId = 9999L;
        long baseOrderId = 5L << 48; // Bot ID 5 prefix

        long startNanos = System.nanoTime();

        // Alternating buy/sell pairs crossing the book:
        // BUY rests and borrows from pool, SELL immediately matches, executes, and releases back to pool!
        for (int i = 0; i < orders; i++) {
            long orderId = baseOrderId | (i + 1);
            Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
            long price = 50000L;
            long qty = 1;
            long timestamp = System.currentTimeMillis();

            publisher.publishPlaceOrder(orderId, userId, side, price, qty, OrderType.LIMIT, timestamp);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMillis = elapsedNanos / 1_000_000.0;
        double ordersPerSec = ((double) orders / elapsedNanos) * 1_000_000_000.0;

        BenchmarkMetrics metrics = new BenchmarkMetrics(
                orders,
                elapsedNanos,
                elapsedMillis,
                ordersPerSec,
                orderPool.getAvailableCount()
        );

        System.out.printf("[BENCHMARK COMPLETED] %d orders in %.2f ms (%.2f ops/sec)\n",
                orders, elapsedMillis, ordersPerSec);
        System.out.println("=======================================================\n");

        return ResponseEntity.ok(metrics);
    }
}
