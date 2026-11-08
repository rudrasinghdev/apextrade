package com.rudra.apextrade.network;

import com.rudra.apextrade.egress.SocketExecutionDispatcher;
import com.rudra.apextrade.ingress.OrderIngressHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class EngineServer {

    private final int port;
    private final OrderIngressHandler ingressHandler;
    private final SocketExecutionDispatcher executionDispatcher;
    private final Thread serverThread;

    public EngineServer(int port, OrderIngressHandler ingressHandler, SocketExecutionDispatcher executionDispatcher) {
        this.port = port;
        this.ingressHandler = ingressHandler;
        this.executionDispatcher = executionDispatcher;
        this.serverThread = new Thread(this::runLoop, "engine-nio-server");
        this.serverThread.setDaemon(false);
    }

    public void start() {
        this.serverThread.start();
    }

    private void runLoop() {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[S2 Engine TCP Server] Listening on port " + port);

            while (selector.isOpen()) {
                selector.select();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(selector, serverChannel);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (IOException e) {
                        System.err.println("[S2 Engine TCP Server] Gateway disconnected with error: " + e.getMessage());
                        closeConnection(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[S2 Engine TCP Server] Fatal socket error: " + e.getMessage());
        }
    }

    private void handleAccept(Selector selector, ServerSocketChannel ssc) throws IOException {
        SocketChannel sc = ssc.accept();
        if (sc != null) {
            sc.configureBlocking(false);
            sc.setOption(StandardSocketOptions.TCP_NODELAY, true);
            executionDispatcher.setSocketChannel(sc);

            ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 64);
            sc.register(selector, SelectionKey.OP_READ, readBuffer);
            System.out.println("[S2 Engine TCP Server] Gateway S1 connected from: " + sc.getRemoteAddress());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        int bytesRead = sc.read(buffer);
        if (bytesRead == -1) {
            System.out.println("[S2 Engine TCP Server] Gateway S1 disconnected.");
            closeConnection(key);
            return;
        }

        buffer.flip();
        ingressHandler.handle(buffer);
        buffer.compact();
    }

    private void closeConnection(SelectionKey key) {
        executionDispatcher.setSocketChannel(null);
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {}
    }
}
