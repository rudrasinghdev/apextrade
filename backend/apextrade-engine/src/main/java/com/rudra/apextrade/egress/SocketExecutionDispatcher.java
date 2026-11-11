package com.rudra.apextrade.egress;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.domain.ExecutionType;
import com.rudra.apextrade.common.wire.ExecutionWireCodec;
import com.rudra.apextrade.common.event.ExecutionEvent;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketExecutionDispatcher implements EventHandler<ExecutionEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);
    @Setter
    private volatile SocketChannel socketChannel;

    @Override
    public void onEvent(ExecutionEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (directBuffer.remaining() < ExecutionWireCodec.FRAME_SIZE) {
            flush();
        }

        if (event.getExecType() == ExecutionType.TRADE) {
            ExecutionWireCodec.encodeTrade(
                    directBuffer,
                    event.getTradeId(),
                    event.getBuyerId(),
                    event.getBuyerOrderId(),
                    event.getSellerId(),
                    event.getSellerOrderId(),
                    event.getTakerSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getTimestamp()
            );
        } else if (event.getExecType() == ExecutionType.CANCELLED) {
            ExecutionWireCodec.encodeCancel(
                    directBuffer,
                    event.getBuyerId(),
                    event.getBuyerOrderId(),
                    event.getSellerId(),
                    event.getSellerOrderId(),
                    event.getTakerSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getTimestamp()
            );
        }

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
