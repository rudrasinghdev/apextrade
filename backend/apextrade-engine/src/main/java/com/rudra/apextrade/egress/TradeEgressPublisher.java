package com.rudra.apextrade.egress;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.core.trade.TradeListener;

public class TradeEgressPublisher implements TradeListener {

    private final RingBuffer<TradeEvent> ringBuffer;

    public TradeEgressPublisher(RingBuffer<TradeEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onTrade(long tradeId, long makerOrderId, long takerOrderId,
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