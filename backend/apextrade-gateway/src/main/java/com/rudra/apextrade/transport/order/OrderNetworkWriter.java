package com.rudra.apextrade.transport.order;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.domain.CommandType;
import com.rudra.apextrade.common.event.OrderCommandEvent;
import com.rudra.apextrade.common.wire.OrderCommandCodec;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Slf4j
public class OrderNetworkWriter implements EventHandler<OrderCommandEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);

    @Setter
    private volatile SocketChannel socketChannel;

    public OrderNetworkWriter() {
        this.socketChannel = null;
    }

    @Override
    public void onEvent(OrderCommandEvent event, long sequence, boolean endOfBatch) throws Exception {
        int requiredSize = (event.getCommandType() == CommandType.PLACE_ORDER)
                ? OrderCommandCodec.NEW_ORDER_FRAME_SIZE
                : OrderCommandCodec.CANCEL_FRAME_SIZE;

        if (directBuffer.remaining() < requiredSize) {
            flush();
        }

        if (event.getCommandType() == CommandType.PLACE_ORDER) {
            OrderCommandCodec.encodePlaceOrder(
                    directBuffer,
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getOrderType()
            );
            log.info("[Gateway Egress] Encoded Order #{} into 40-byte wire frame in egress buffer.", event.getOrderId());
        } else if (event.getCommandType() == CommandType.CANCEL_ORDER) {
            OrderCommandCodec.encodeCancelOrder(
                    directBuffer,
                    event.getUserId(),
                    event.getOrderId()
            );
            log.info("[Gateway Egress] Encoded Cancel Order #{} into 20-byte wire frame in egress buffer.", event.getOrderId());
        }

        if (endOfBatch) {
            flush();
        }
    }

    private void flush() throws IOException {
        directBuffer.flip();
        int bytesToWrite = directBuffer.remaining();
        if (bytesToWrite == 0) {
            directBuffer.clear();
            return;
        }

        if (socketChannel != null && socketChannel.isConnected()) {
            int totalWritten = 0;
            while (directBuffer.hasRemaining()) {
                totalWritten += socketChannel.write(directBuffer);
            }
            log.info("[Gateway Egress] Flushed {} bytes to Engine TCP socket.", totalWritten);
        } else {
            log.warn("[Gateway Egress] Socket channel not connected! Dropped {} bytes of pending order frames.", bytesToWrite);
        }
        directBuffer.clear();
    }
}
