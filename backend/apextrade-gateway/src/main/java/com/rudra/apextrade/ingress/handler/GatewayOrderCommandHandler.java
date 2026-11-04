package com.rudra.apextrade.ingress.handler;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderWireCodec;
import com.rudra.apextrade.ingress.event.OrderCommandEvent;

import java.nio.ByteBuffer;

/**
 * Single-threaded socket serializer handler running on Gateway's carrier thread.
 * Drains Gateway's MPSC Ingress RingBuffer and encodes events into wire frames.
 * Provides micro-batching via endOfBatch signal.
 */
public class GatewayOrderCommandHandler implements EventHandler<OrderCommandEvent> {

    private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024 * 64);
    private long processedCount = 0;

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
            // Flushes batched direct buffer to TCP socket channel
            directBuffer.flip();
            // In TCP mode, this calls socketChannel.write(directBuffer)
            directBuffer.clear();
        }
    }

    public long getProcessedCount() {
        return processedCount;
    }
}

