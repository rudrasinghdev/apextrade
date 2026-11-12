package com.rudra.apextrade.transport.execution;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.common.event.ExecutionEvent;
import com.rudra.apextrade.common.wire.ExecutionWireCodec;

/**
 * Feeds execution events (trades and cancel confirmations) from the TCP socket reader thread
 * into the S1 Gateway Execution Disruptor.
 */
public class ExecutionEventPublisher implements ExecutionWireCodec.Handler {

    private final RingBuffer<ExecutionEvent> ringBuffer;

    public ExecutionEventPublisher(RingBuffer<ExecutionEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onTrade(
            long tradeId,
            long buyerUserId,
            long buyerOrderId,
            long sellerUserId,
            long sellerOrderId,
            long price,
            long quantity,
            long timestamp
    ) {
        long sequence = ringBuffer.next();
        try {
            ExecutionEvent event = ringBuffer.get(sequence);
            event.setTrade(
                    tradeId,
                    buyerUserId,
                    buyerOrderId,
                    sellerUserId,
                    sellerOrderId,
                    price,
                    quantity,
                    timestamp
            );
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void onCancelled(
            long orderId,
            long userId,
            Side side,
            long price,
            long unfilledQuantity,
            long timestamp
    ) {
        long sequence = ringBuffer.next();
        try {
            ExecutionEvent event = ringBuffer.get(sequence);
            event.setCancelled(
                    orderId,
                    userId,
                    side,
                    price,
                    unfilledQuantity,
                    timestamp
            );
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void onCancelRejected(
            long orderId,
            long userId,
            long timestamp
    ) {
        long sequence = ringBuffer.next();
        try {
            ExecutionEvent event = ringBuffer.get(sequence);
            event.setCancelRejected(
                    orderId,
                    userId,
                    timestamp
            );
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
