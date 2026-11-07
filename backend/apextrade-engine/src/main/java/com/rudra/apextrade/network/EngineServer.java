package com.rudra.apextrade.network;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.OrderWireCodec;
import com.rudra.apextrade.common.OrderWireHandler;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.egress.SocketTradeDispatcher;
import com.rudra.apextrade.ingress.OrderCommandPublisher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Non-blocking NIO TCP Server listening on port 9000 for S1 Gateway.
 * Accepts full-duplex SocketChannel, binds the Egress dispatcher, and streams
 * incoming 40-byte order frames into the Ingress Disruptor.
 */
public class EngineServer implements Runnable, AutoCloseable {

    private final int port;
    private final OrderCommandPublisher publisher;
    private final SocketTradeDispatcher tradeDispatcher;
    private volatile boolean running = true;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    public EngineServer(int port, OrderCommandPublisher publisher, SocketTradeDispatcher tradeDispatcher) {
        this.port = port;
        this.publisher = publisher;
        this.tradeDispatcher = tradeDispatcher;
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[S2 Engine TCP Server] Listening on port " + port);

            OrderWireHandler wireHandler = new OrderWireHandler() {
                @Override
                public void onNewOrder(long orderId, long userId, Side side, OrderType orderType, long price, long quantity) {
                    publisher.publishPlaceOrder(orderId, userId, side, price, quantity, orderType, System.nanoTime());
                }

                @Override
                public void onCancelOrder(long orderId, long userId) {
                    publisher.publishCancelOrder(orderId, System.nanoTime());
                }
            };

            while (running && selector.isOpen()) {
                int selected = selector.select(500);
                if (selected == 0) {
                    continue;
                }

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key, wireHandler);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[S2 Engine TCP Server] Error in selector loop: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        if (sc != null) {
            sc.configureBlocking(false);
            sc.setOption(StandardSocketOptions.TCP_NODELAY, true);

            // Share the full-duplex socket with the Egress Dispatcher!
            tradeDispatcher.setSocketChannel(sc);

            ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 64);
            sc.register(selector, SelectionKey.OP_READ, readBuffer);
            System.out.println("[S2 Engine TCP Server] Gateway S1 connected from: " + sc.getRemoteAddress());
        }
    }

    private void handleRead(SelectionKey key, OrderWireHandler handler) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            int bytesRead = sc.read(buffer);
            if (bytesRead == -1) {
                System.out.println("[S2 Engine TCP Server] Gateway S1 disconnected.");
                tradeDispatcher.setSocketChannel(null);
                key.cancel();
                sc.close();
                return;
            }

            buffer.flip();
            OrderWireCodec.decodeFrames(buffer, handler);
            buffer.compact();
        } catch (IOException e) {
            System.err.println("[S2 Engine TCP Server] Socket read error: " + e.getMessage());
            tradeDispatcher.setSocketChannel(null);
            try {
                key.cancel();
                sc.close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException ignored) {}
    }
}
