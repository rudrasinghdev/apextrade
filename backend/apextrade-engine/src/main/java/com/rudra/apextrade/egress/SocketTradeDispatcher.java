package com.rudra.apextrade.egress;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.TradeWireCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Consumes executed trades from Egress Disruptor on Thread 3 ("engine-trade-egress").
 * Micro-batches trades into a 64KB DirectBuffer and flushes to the TCP socket on endOfBatch.
 */
public class SocketTradeDispatcher implements EventHandler<TradeEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);
    private volatile SocketChannel socketChannel;

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void onEvent(TradeEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (directBuffer.remaining() < TradeWireCodec.FRAME_SIZE) {
            flush();
        }

        TradeWireCodec.encodeTrade(
                directBuffer,
                event.getTradeId(),
                event.getMakerOrderId(),
                event.getTakerOrderId(),
                event.getTakerSide(),
                event.getPrice(),
                event.getQuantity(),
                event.getTimestamp()
        );

        if (endOfBatch) {
            flush();
        }
    }

    public void flush() throws IOException {
        directBuffer.flip();
        SocketChannel channel = this.socketChannel;
        if (channel != null && channel.isOpen() && channel.isConnected()) {
            while (directBuffer.hasRemaining()) {
                channel.write(directBuffer);
            }
        }
        directBuffer.clear();
    }
}
