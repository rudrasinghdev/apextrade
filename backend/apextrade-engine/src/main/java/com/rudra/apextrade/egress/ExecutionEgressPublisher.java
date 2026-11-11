package com.rudra.apextrade.egress;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.wire.ExecutionWireCodec;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.common.event.ExecutionEvent;

public class ExecutionEgressPublisher implements ExecutionWireCodec.Handler {

    private final RingBuffer<ExecutionEvent> ringBuffer;

    public ExecutionEgressPublisher(RingBuffer<ExecutionEvent> ringBuffer) {
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
