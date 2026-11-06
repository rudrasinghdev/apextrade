package com.rudra.apextrade.ingress;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.ingress.handler.SocketWireDispatchHandler;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class IngressPipelineSmokeTest {

    @Autowired
    private OrderCommandPublisher publisher;

    @Autowired
    private SocketWireDispatchHandler handler;

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
    @DisplayName("Gateway Ingress: Orders published to Disruptor are processed by socket serializer handler")
    void testGatewayIngressPipeline() throws Exception {
        long initialProcessed = handler.getProcessedCount();

        publisher.publishPlaceOrder(1001L, 101L, Side.SELL, 6_500_000L, 100L, OrderType.LIMIT, System.nanoTime());
        publisher.publishPlaceOrder(1002L, 102L, Side.BUY, 6_500_000L, 100L, OrderType.LIMIT, System.nanoTime());
        publisher.publishCancelOrder(1001L, 101L, System.nanoTime());

        waitUntil(() -> {
            assertTrue(handler.getProcessedCount() >= initialProcessed + 3,
                    "Handler must process all 3 published commands");
        }, 1000);
    }
}
