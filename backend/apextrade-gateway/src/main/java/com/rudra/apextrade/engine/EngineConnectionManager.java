package com.rudra.apextrade.engine;

import com.rudra.apextrade.engine.order.SocketWireDispatchHandler;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

@Slf4j
@Component
public class EngineConnectionManager implements ApplicationRunner, AutoCloseable {

    private final SocketWireDispatchHandler dispatchHandler;
    private final String engineHost;
    private final int enginePort;
    private SocketChannel socketChannel;

    public EngineConnectionManager(
            SocketWireDispatchHandler dispatchHandler,
            @Value("") String engineHost,
            @Value("") int enginePort
    ) {
        this.dispatchHandler = dispatchHandler;
        this.engineHost = engineHost;
        this.enginePort = enginePort;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("[S1 Gateway] Connecting to S2 Matching Engine at {}:{}", engineHost, enginePort);
        try {
            this.socketChannel = SocketChannel.open();
            this.socketChannel.configureBlocking(false);
            this.socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            this.socketChannel.connect(new InetSocketAddress(engineHost, enginePort));

            long timeoutDeadline = System.currentTimeMillis() + 5000;
            while (!this.socketChannel.finishConnect()) {
                if (System.currentTimeMillis() > timeoutDeadline) {
                    throw new IOException("Connection timed out after 5000ms");
                }
                Thread.onSpinWait();
            }

            dispatchHandler.setSocketChannel(this.socketChannel);
            log.info("[S1 Gateway] Connected to S2 Matching Engine on port {}", enginePort);
        } catch (IOException e) {
            log.error("[S1 Gateway] Failed to connect to S2 Matching Engine: {}", e.getMessage());
            throw new IllegalStateException("Matching Engine unavailable at " + engineHost + ":" + enginePort, e);
        }
    }

    @Override
    public void close() {
        if (socketChannel != null && socketChannel.isOpen()) {
            try {
                socketChannel.close();
                log.info("[S1 Gateway] SocketChannel to S2 closed.");
            } catch (IOException e) {
                log.warn("[S1 Gateway] Error closing SocketChannel: {}", e.getMessage());
            }
        }
    }
}
