package com.rudra.apextrade.core.trade;

import com.rudra.apextrade.common.Side;

@FunctionalInterface
public interface TradeListener {

    void onTrade(
            long tradeId,
            long makerOrderId,
            long takerOrderId,
            Side takerSide,
            long price,
            long quantity,
            long timestamp
    );
}
