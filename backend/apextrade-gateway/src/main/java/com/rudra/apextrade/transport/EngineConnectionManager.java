package com.rudra.apextrade.transport;

import com.rudra.apextrade.common.wire.ExecutionWireCodec;
import com.rudra.apextrade.transport.execution.ExecutionEventPublisher;
import com.rudra.apextrade.transport.order.OrderNetworkWriter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@Component
public class EngineConnectionManager implements ApplicationRunner, AutoCloseable {

    private final OrderNetworkWriter networkWriter;
    private final ExecutionEventPublisher executionPublisher;
    private final String engineHost;
    private final int enginePort;
    private SocketChannel socketChannel;
    private volatile boolean running = true;
    private Thread readerThread;

    public EngineConnectionManager(
            OrderNetworkWriter networkWriter,
            ExecutionEventPublisher executionPublisher,
            @Value("${apextrade.engine.host:localhost}") String engineHost,
            @Value("${apextrade.engine.port:9000}") int enginePort
    ) {
        this.networkWriter = networkWriter;
        this.executionPublisher = executionPublisher;
        this.engineHost = engineHost;
        this.enginePort = enginePort;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("[S1 Gateway Transport] Connecting to S2 Matching Engine at {}:{}", engineHost, enginePort);
        connectToEngine();
    }

    private synchronized boolean connectToEngine() {
        try {
            if (this.socketChannel != null && this.socketChannel.isOpen()) {
                try {
                    this.socketChannel.close();
                } catch (IOException ignored) {}
            }

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

            networkWriter.setSocketChannel(this.socketChannel);
            log.info("[S1 Gateway Transport] Connected to S2 Matching Engine on port {}", enginePort);

            startExecutionReaderThread();
            return true;
        } catch (IOException e) {
            log.warn("[S1 Gateway Transport] Could not connect to S2 Matching Engine: {}", e.getMessage());
            return false;
        }
    }

    private void startExecutionReaderThread() {
        if (readerThread != null && readerThread.isAlive()) {
            return;
        }

        readerThread = new Thread(() -> {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);
            log.info("[S1 Gateway Transport] Execution socket reader active.");

            while (running) {
                if (socketChannel == null || !socketChannel.isOpen()) {
                    LockSupport.parkNanos(1_000_000_000L); // 1s backoff before retry
                    if (running) {
                        connectToEngine();
                    }
                    continue;
                }

                try {
                    int bytesRead = socketChannel.read(directBuffer);
                    if (bytesRead == -1) {
                        log.warn("[S1 Gateway Transport] S2 Engine disconnected. Attempting auto-reconnect...");
                        networkWriter.setSocketChannel(null);
                        try {
                            socketChannel.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    if (bytesRead > 0) {
                        directBuffer.flip();
                        ExecutionWireCodec.decodeFrames(directBuffer, executionPublisher);
                        directBuffer.compact();
                    } else {
                        // High-efficiency backoff: spin-wait briefly, then park 10 microseconds to yield CPU
                        LockSupport.parkNanos(10_000L);
                    }
                } catch (IOException e) {
                    if (running) {
                        log.warn("[S1 Gateway Transport] Socket read error from S2: {}. Reconnecting...", e.getMessage());
                        networkWriter.setSocketChannel(null);
                        try {
                            socketChannel.close();
                        } catch (IOException ignored) {}
                    }
                }
            }
        }, "gateway-execution-receiver");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public void close() {
        running = false;
        if (socketChannel != null && socketChannel.isOpen()) {
            try {
                socketChannel.close();
                log.info("[S1 Gateway Transport] SocketChannel to S2 closed.");
            } catch (IOException e) {
                log.warn("[S1 Gateway Transport] Error closing SocketChannel: {}", e.getMessage());
            }
        }
    }
}
