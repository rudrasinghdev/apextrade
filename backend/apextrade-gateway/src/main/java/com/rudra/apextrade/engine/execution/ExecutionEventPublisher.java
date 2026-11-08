package com.rudra.apextrade.engine.execution;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.ExecutionWireCodec;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.common.event.ExecutionEvent;

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
    public void onTrade(long tradeId, long buyerId, long buyerOrderId,
                        long sellerId, long sellerOrderId, Side takerSide,
                        long price, long quantity, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            ExecutionEvent event = ringBuffer.get(sequence);
            event.setTrade(tradeId, buyerId, buyerOrderId, sellerId, sellerOrderId, takerSide, price, quantity, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void onCancel(long buyerId, long buyerOrderId,
                         long sellerId, long sellerOrderId, Side side,
                         long price, long quantity, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            ExecutionEvent event = ringBuffer.get(sequence);
            event.setCancel(buyerId, buyerOrderId, sellerId, sellerOrderId, side, price, quantity, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
