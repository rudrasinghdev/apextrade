package com.rudra.apextrade;

import com.rudra.apextrade.matching.MatchingEngine;
import com.rudra.apextrade.matching.OrderBook;
import com.rudra.apextrade.matching.OrderPool;
import com.rudra.apextrade.egress.EgressDisruptorConfig;
import com.rudra.apextrade.ingress.IngressDisruptorConfig;
import com.rudra.apextrade.ingress.OrderIngressHandler;
import com.rudra.apextrade.network.EngineServer;

public class EngineMain {

    static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   ApexTrade Matching Engine Core (S2) Daemon    ");
        System.out.println("   Deterministic Low-Latency In-Memory Matcher   ");
        System.out.println("=================================================");

        OrderPool orderPool = new OrderPool();
        OrderBook orderBook = new OrderBook("BTC/USDT", orderPool);

        EgressDisruptorConfig egressConfig = new EgressDisruptorConfig();

        MatchingEngine matchingEngine = new MatchingEngine(orderBook, 50000L, egressConfig.getPublisher());

        IngressDisruptorConfig ingressConfig = new IngressDisruptorConfig(matchingEngine);
        OrderIngressHandler ingressHandler = new OrderIngressHandler(ingressConfig.getPublisher());

        int tcpPort = Integer.parseInt(System.getenv().getOrDefault("APEXTRADE_ENGINE_PORT", "9000"));
        EngineServer engineServer = new EngineServer(tcpPort, ingressHandler, egressConfig.getDispatcher());
        engineServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[S2 Engine] Gracefully shutting down Disruptors...");
            ingressConfig.close();
            egressConfig.close();
            System.out.println("[S2 Engine] Shutdown complete.");
        }, "engine-shutdown-hook"));

        System.out.println("[S2 Engine] Ready for high-throughput order execution on port " + tcpPort);
    }
}
