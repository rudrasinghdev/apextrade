package com.rudra.apextrade.egress.handler;

import com.rudra.apextrade.egress.buffer.SpscTradeRingBuffer;
import com.rudra.apextrade.egress.buffer.TradeEvent;
import com.rudra.apextrade.gateway.websocket.TradeWebSocketHandler;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.locks.LockSupport;

public class TradeEgressHandler implements Runnable {

    private final SpscTradeRingBuffer ringBuffer;
    private final TradeWebSocketHandler webSocketHandler;
    private final Thread workerThread;
    private volatile boolean running = true;

    public TradeEgressHandler(SpscTradeRingBuffer ringBuffer, TradeWebSocketHandler webSocketHandler) {
        this.ringBuffer = ringBuffer;
        this.webSocketHandler = webSocketHandler;

        this.workerThread = new Thread(this, "trade-egress-thread");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public void run() {
        while (running) {
            TradeEvent event = ringBuffer.peek();

            if (event != null) {
                String json = serializeToJson(event);

                ringBuffer.advance();

                webSocketHandler.broadcast(json);
            } else {
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    private String serializeToJson(TradeEvent e) {
        return "{\"type\":\"TRADE\",\"tradeId\":" + e.getTradeId() +
                ",\"makerOrderId\":" + e.getMakerOrderId() +
                ",\"takerOrderId\":" + e.getTakerOrderId() +
                ",\"takerSide\":\"" + e.getTakerSide() + "\"" +
                ",\"price\":" + e.getPrice() +
                ",\"quantity\":" + e.getQuantity() +
                ",\"timestamp\":" + e.getTimestamp() + "}";
    }

    @PreDestroy
    public void shutdown() {
        this.running = false;
        this.workerThread.interrupt();
    }
}