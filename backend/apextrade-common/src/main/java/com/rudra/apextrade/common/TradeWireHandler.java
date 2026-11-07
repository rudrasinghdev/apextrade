package com.rudra.apextrade.common;

public interface TradeWireHandler {
    void onTrade(long tradeId, long makerOrderId, long takerOrderId,
                 Side takerSide, long price, long quantity, long timestamp);
}