package com.rudra.apextrade.websocket;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TradeWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final AtomicReference<String> latestLtp = new AtomicReference<>("50000.00");

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 100, 512 * 1024);
        sessions.add(safeSession);

        // Immediate snapshot push on connect to eliminate UI cold-start staleness
        try {
            String snapshotJson = String.format(
                    "{\"type\":\"SNAPSHOT\",\"symbol\":\"BTC/USDT\",\"price\":\"%s\",\"timestamp\":%d}",
                    latestLtp.get(),
                    System.currentTimeMillis()
            );
            safeSession.sendMessage(new TextMessage(snapshotJson));
        } catch (Exception ignored) {}
    }

    public void updateLatestPrice(String price) {
        if (price != null && !price.isBlank()) {
            latestLtp.set(price);
        }
    }

    public String getLatestPrice() {
        return latestLtp.get();
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable  exception) {
        sessions.remove(session);
    }

    public void broadcast(String jsonPayload) {
        if (sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(jsonPayload);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (Exception e) {
                sessions.remove(session);
            }
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
