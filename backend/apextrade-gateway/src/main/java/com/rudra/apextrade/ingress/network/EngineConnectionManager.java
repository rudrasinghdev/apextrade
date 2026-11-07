package com.rudra.apextrade.ingress.network;

import com.rudra.apextrade.ingress.handler.SocketWireDispatchHandler;
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
            @Value("${apextrade.engine.host:localhost}") String engineHost,
            @Value("${apextrade.engine.port:9000}") int enginePort
    ) {
        this.dispatchHandler = dispatchHandler;
        this.engineHost = engineHost;
        this.enginePort = enginePort;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("Connecting to Matching Engine Core (S2) at {}:{}...", engineHost, enginePort);
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(true);
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            socketChannel.connect(new InetSocketAddress(engineHost, enginePort));
            socketChannel.configureBlocking(false);

            dispatchHandler.setSocketChannel(socketChannel);
            log.info("CONNECTED to Matching Engine Core (S2) on port {}", enginePort);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to connect to Matching Engine Core at {}:{}. Engine must be running first!",
                    engineHost, enginePort, e);
            throw new IllegalStateException("Matching Engine unavailable at " + engineHost + ":" + enginePort
                    + ". Aborting Gateway startup.", e);
        }
    }

    @Override
    public void close() {
        dispatchHandler.setSocketChannel(null);
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException ignored) {
            }
        }
        log.info("Engine connection closed.");
    }
}