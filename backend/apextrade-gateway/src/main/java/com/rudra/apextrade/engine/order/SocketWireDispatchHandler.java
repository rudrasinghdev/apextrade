package com.rudra.apextrade.engine.order;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderWireCodec;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketWireDispatchHandler implements EventHandler<OrderCommandEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);

    @Setter
    private volatile SocketChannel socketChannel;

    public SocketWireDispatchHandler() {
        this.socketChannel = null;
    }

    @Override
    public void onEvent(OrderCommandEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getCommandType() == null) {
            return;
        }

        if (directBuffer.remaining() < OrderWireCodec.FRAME_SIZE) {
            flush();
        }

        if (event.getCommandType() == CommandType.PLACE_ORDER) {
            OrderWireCodec.encodeNewOrder(
                    directBuffer,
                    event.getOrderId(),
                    (int) event.getUserId(),
                    event.getSide(),
                    event.getOrderType(),
                    event.getPrice(),
                    event.getQuantity()
            );
        } else if (event.getCommandType() == CommandType.CANCEL_ORDER) {
            OrderWireCodec.encodeCancelOrder(
                    directBuffer,
                    event.getOrderId(),
                    (int) event.getUserId()
            );
        }

        if (endOfBatch) {
            flush();
        }
    }

    private void flush() throws IOException {
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
