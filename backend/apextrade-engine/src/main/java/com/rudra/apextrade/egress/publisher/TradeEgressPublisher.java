package com.rudra.apextrade.egress.publisher;

import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.core.trade.TradeListener;
import com.rudra.apextrade.egress.buffer.SpscTradeRingBuffer;

public class TradeEgressPublisher implements TradeListener {

    private final SpscTradeRingBuffer ringBuffer;

    public TradeEgressPublisher(SpscTradeRingBuffer ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onTrade(long tradeId, long makerOrderId, long takerOrderId,
                        Side takerSide, long price, long quantity, long timestamp) {
        ringBuffer.offer(tradeId, makerOrderId, takerOrderId, takerSide, price, quantity, timestamp);
    }
}
