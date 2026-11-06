package com.rudra.apextrade.ingress.network;

import com.rudra.apextrade.ingress.handler.SocketWireDispatchHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Slf4j
@Component
public class EngineConnectionManager implements SmartLifecycle, Runnable {

    private final SocketWireDispatchHandler dispatchHandler;
    private final String engineHost;
    private final int enginePort;
    private final long reconnectIntervalMs;

    private volatile boolean running = false;
    private Thread connectionThread;

    public EngineConnectionManager(
            SocketWireDispatchHandler dispatchHandler,
            @Value("${apextrade.engine.host:localhost}") String engineHost,
            @Value("${apextrade.engine.port:9000}") int enginePort,
            @Value("${apextrade.engine.reconnect-interval-ms:2000}") long reconnectIntervalMs
    ) {
        this.dispatchHandler = dispatchHandler;
        this.engineHost = engineHost;
        this.enginePort = enginePort;
        this.reconnectIntervalMs = reconnectIntervalMs;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        connectionThread = new Thread(this, "engine-conn-manager");
        connectionThread.setDaemon(true);
        connectionThread.start();
        log.info("EngineConnectionManager initialized for target {}:{}", engineHost, enginePort);
    }

    @Override
    public void run() {
        while (running) {
            SocketChannel channel = null;
            try {
                log.info("Connecting to S2 Matching Engine at {}:{}...", engineHost, enginePort);
                channel = SocketChannel.open();
                channel.configureBlocking(true);
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                channel.connect(new InetSocketAddress(engineHost, enginePort));

                dispatchHandler.setSocketChannel(channel);
                log.info("CONNECTED to S2 Matching Engine at {}:{} [TCP_NODELAY=true]", engineHost, enginePort);

                ByteBuffer readerBuf = ByteBuffer.allocate(1024);
                while (running && channel.isOpen()) {
                    int bytesRead = channel.read(readerBuf);
                    if (bytesRead == -1) {
                        log.warn("S2 Matching Engine closed the connection (EOF)");
                        break;
                    }
                    readerBuf.clear();
                }
            } catch (IOException e) {
                log.warn("Connection to S2 Engine at {}:{} failed ({}) - retrying in {}ms...",
                        engineHost, enginePort, e.getMessage(), reconnectIntervalMs);
            } finally {
                dispatchHandler.setSocketChannel(null);
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            if (running) {
                try {
                    Thread.sleep(reconnectIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        dispatchHandler.setSocketChannel(null);
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
        log.info("EngineConnectionManager stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

