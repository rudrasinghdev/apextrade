package com.rudra.apextrade;

import com.sun.net.httpserver.HttpServer;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.egress.EgressDisruptorConfig;
import com.rudra.apextrade.ingress.IngressDisruptorConfig;
import com.rudra.apextrade.network.EngineServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Standalone low-latency execution entry point for S2 Matching Engine Core.
 * Runs with pure Java SE (zero framework bloat, zero GC during active trading).
 */
public class EngineMain {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   ApexTrade Matching Engine Core (S2) Daemon    ");
        System.out.println("   Deterministic Low-Latency In-Memory Matcher   ");
        System.out.println("=================================================");

        // 1. Start embedded zero-dependency HTTP health server for Render ($PORT)
        startHealthCheckServer();

        // 2. Initialize Core Matcher State Machine & Order Pool
        OrderPool orderPool = new OrderPool();
        OrderBook orderBook = new OrderBook("BTC/USDT", orderPool);

        // 3. Initialize Egress SPSC Disruptor Pipeline (Trade Events -> Network)
        EgressDisruptorConfig egressConfig = new EgressDisruptorConfig();

        // 4. Initialize Core Matching Engine (wires TradeEgressPublisher as TradeListener)
        MatchingEngine matchingEngine = new MatchingEngine(orderBook, 50000L, egressConfig.getPublisher());

        // 5. Initialize Ingress SPSC Disruptor Pipeline (Network -> Order Commands -> Matcher)
        IngressDisruptorConfig ingressConfig = new IngressDisruptorConfig(matchingEngine);

        // 6. Start TCP Server on Port 9000 (binds full-duplex socket to Egress Dispatcher)
        int tcpPort = 9000;
        EngineServer engineServer = new EngineServer(tcpPort, ingressConfig.getPublisher(), egressConfig.getDispatcher());
        Thread serverThread = new Thread(engineServer, "engine-nio-server");
        serverThread.setDaemon(false); // Non-daemon keeps JVM alive
        serverThread.start();

        System.out.println("[S2 Engine] Ready for high-throughput order execution.");
    }

    private static void startHealthCheckServer() {
        try {
            int httpPort = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(httpPort), 0);

            server.createContext("/", exchange -> {
                byte[] response = "{\"status\":\"UP\",\"service\":\"apextrade-engine\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });

            server.createContext("/health", exchange -> {
                byte[] response = "{\"status\":\"UP\",\"service\":\"apextrade-engine\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("[S2 Health Server] Listening on HTTP port " + httpPort);
        } catch (Exception e) {
            System.err.println("[S2 Health Server] Failed to bind HTTP health server: " + e.getMessage());
        }
    }
}
