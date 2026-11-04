package com.rudra.apextrade.core.engine;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.egress.buffer.SpscTradeRingBuffer;
import com.rudra.apextrade.egress.handler.TradeEgressHandler;
import com.rudra.apextrade.egress.publisher.TradeEgressPublisher;

/**
 * Standalone low-latency execution entry point for S2 Matching Engine Core.
 */
public class EngineMain {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   ApexTrade Matching Engine Core (S2) Daemon    ");
        System.out.println("   Deterministic Low-Latency In-Memory Matcher   ");
        System.out.println("=================================================");

        OrderPool orderPool = new OrderPool();
        OrderBook orderBook = new OrderBook("BTC/USDT", orderPool);
        SpscTradeRingBuffer ringBuffer = new SpscTradeRingBuffer(65536);
        TradeEgressPublisher tradeEgressPublisher = new TradeEgressPublisher(ringBuffer);
        MatchingEngine engine = new MatchingEngine(orderBook, 50000L, tradeEgressPublisher);

        TradeEgressHandler egressHandler = new TradeEgressHandler(ringBuffer, tradeJson -> {
            System.out.println("[S2 Egress Broadcast] " + tradeJson);
        });

        System.out.println("[S2 Engine] Ready for high-throughput order execution.");
    }
}

