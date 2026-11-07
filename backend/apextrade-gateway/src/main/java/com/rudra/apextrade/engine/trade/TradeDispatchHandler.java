package com.rudra.apextrade.engine.trade;

import com.lmax.disruptor.EventHandler;

public class TradeDispatchHandler implements EventHandler<TradeEvent> {
    @Override
    public void onEvent(TradeEvent event, long sequence, boolean endOfBatch) throws Exception {
        // Stub - will settle AccountLedger and broadcast to WebSocket
    }
}
