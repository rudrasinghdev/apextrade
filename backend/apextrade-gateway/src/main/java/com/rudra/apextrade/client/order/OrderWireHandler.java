package com.rudra.apextrade.client.order;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderCommandCodec;
import com.rudra.apextrade.common.event.OrderCommandEvent;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class OrderWireHandler implements EventHandler<OrderCommandEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);

    @Setter
    private volatile SocketChannel socketChannel;

    public OrderWireHandler() {
        this.socketChannel = null;
    }

    @Override
    public void onEvent(OrderCommandEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getCommandType() == null) {
            return;
        }

        if (directBuffer.remaining() < OrderCommandCodec.FRAME_SIZE) {
            flush();
        }

        if (event.getCommandType() == CommandType.PLACE_ORDER) {
            OrderCommandCodec.encodeNewOrder(
                    directBuffer,
                    event.getOrderId(),
                    event.getUserId(),
                    event.getSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getOrderType()
            );
        } else if (event.getCommandType() == CommandType.CANCEL_ORDER) {
            OrderCommandCodec.encodeCancelOrder(
                    directBuffer,
                    event.getOrderId(),
                    event.getUserId()
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

