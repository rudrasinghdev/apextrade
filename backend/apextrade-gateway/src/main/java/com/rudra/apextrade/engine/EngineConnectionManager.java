package com.rudra.apextrade.engine;

import com.rudra.apextrade.common.ExecutionWireCodec;
import com.rudra.apextrade.engine.execution.ExecutionEventPublisher;
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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Slf4j
@Component
public class EngineConnectionManager implements ApplicationRunner, AutoCloseable {

    private final SocketWireDispatchHandler dispatchHandler;
    private final ExecutionEventPublisher executionPublisher;
    private final String engineHost;
    private final int enginePort;
    private SocketChannel socketChannel;
    private volatile boolean running = true;
    private Thread readerThread;

    public EngineConnectionManager(
            SocketWireDispatchHandler dispatchHandler,
            ExecutionEventPublisher executionPublisher,
            @Value("${apextrade.engine.host:localhost}") String engineHost,
            @Value("${apextrade.engine.port:9000}") int enginePort
    ) {
        this.dispatchHandler = dispatchHandler;
        this.executionPublisher = executionPublisher;
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

            // Start dedicated daemon socket reader thread for incoming executions
            startExecutionReaderThread();
        } catch (IOException e) {
            log.error("[S1 Gateway] Failed to connect to S2 Matching Engine: {}", e.getMessage());
            throw new IllegalStateException("Matching Engine unavailable at " + engineHost + ":" + enginePort, e);
        }
    }

    private void startExecutionReaderThread() {
        readerThread = new Thread(() -> {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);
            log.info("[S1 Gateway] Execution socket reader active.");

            while (running && socketChannel.isOpen()) {
                try {
                    int bytesRead = socketChannel.read(directBuffer);
                    if (bytesRead == -1) {
                        log.warn("[S1 Gateway] S2 Engine disconnected.");
                        break;
                    }

                    if (bytesRead > 0) {
                        directBuffer.flip();
                        ExecutionWireCodec.decodeFrames(directBuffer, executionPublisher);
                        directBuffer.compact();
                    } else {
                        Thread.onSpinWait();
                    }
                } catch (IOException e) {
                    if (running) {
                        log.error("[S1 Gateway] Socket read error from S2: {}", e.getMessage());
                    }
                    break;
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
                log.info("[S1 Gateway] SocketChannel to S2 closed.");
            } catch (IOException e) {
                log.warn("[S1 Gateway] Error closing SocketChannel: {}", e.getMessage());
            }
        }
    }
}
