package com.rudra.apextrade.core.event;

import com.rudra.apextrade.core.order.Side;

public record Trade (
    long tradeId,
    long makerOrderId,
    long takerOrderId,
    Side takerSide,
    long price,
    long quantity,
    long timestamp
) {}
