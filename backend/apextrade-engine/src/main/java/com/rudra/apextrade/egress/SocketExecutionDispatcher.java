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
        int requiredSize;
        if (event.getExecType() == ExecutionType.TRADE) {
            requiredSize = ExecutionWireCodec.TRADE_FRAME_SIZE;
        } else if (event.getExecType() == ExecutionType.CANCELLED) {
            requiredSize = ExecutionWireCodec.CANCEL_FRAME_SIZE;
        } else {
            requiredSize = ExecutionWireCodec.CANCEL_REJECT_FRAME_SIZE;
        }

        if (directBuffer.remaining() < requiredSize) {
            flush();
        }

        if (event.getExecType() == ExecutionType.TRADE) {
            ExecutionWireCodec.encodeTrade(
                    directBuffer,
                    event.getTradeId(),
                    event.getBuyerUserId(),
                    event.getBuyerOrderId(),
                    event.getSellerUserId(),
                    event.getSellerOrderId(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getTimestamp()
            );
        } else if (event.getExecType() == ExecutionType.CANCELLED) {
            ExecutionWireCodec.encodeCancel(
                    directBuffer,
                    event.getCancelledOrderId(),
                    event.getCancelledUserId(),
                    event.getCancelledSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getTimestamp()
            );
        } else if (event.getExecType() == ExecutionType.CANCEL_REJECTED) {
            ExecutionWireCodec.encodeCancelReject(
                    directBuffer,
                    event.getCancelledOrderId(),
                    event.getCancelledUserId(),
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
