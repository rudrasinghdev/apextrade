package com.rudra.apextrade.engine.trade;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.Side;

/**
 * Feeds executed trades from the TCP socket reader thread into S1 Trade Disruptor.
 */
public class TradeEventPublisher {

    private final RingBuffer<TradeEvent> ringBuffer;

    public TradeEventPublisher(RingBuffer<TradeEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void publishTrade(long tradeId, long makerOrderId, long takerOrderId,
                             Side takerSide, long price, long quantity, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            TradeEvent event = ringBuffer.get(sequence);
            event.set(tradeId, makerOrderId, takerOrderId, takerSide, price, quantity, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}