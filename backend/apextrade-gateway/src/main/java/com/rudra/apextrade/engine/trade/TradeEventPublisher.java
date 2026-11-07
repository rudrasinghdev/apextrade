package com.rudra.apextrade.engine.trade;

import com.lmax.disruptor.RingBuffer;

public class TradeEventPublisher {
    private final RingBuffer<TradeEvent> ringBuffer;

    public TradeEventPublisher(RingBuffer<TradeEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }
}
