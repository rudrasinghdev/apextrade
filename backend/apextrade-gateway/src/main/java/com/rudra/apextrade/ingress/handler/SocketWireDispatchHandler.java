package com.rudra.apextrade.ingress.handler;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderWireCodec;
import com.rudra.apextrade.ingress.event.OrderCommandEvent;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketWireDispatchHandler implements EventHandler<OrderCommandEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);

    @Setter
    private volatile SocketChannel socketChannel;
    private long processedCount = 0;

    public SocketWireDispatchHandler() {
        this.socketChannel = null;
    }

    public boolean isConnected() {
        SocketChannel channel = this.socketChannel;
        return channel != null && channel.isOpen() && channel.isConnected();
    }

    @Override
    public void onEvent(OrderCommandEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getCommandType() == null) {
            return;
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

        processedCount++;

        if (endOfBatch) {
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

    public long getProcessedCount() {
        return processedCount;
    }
}
