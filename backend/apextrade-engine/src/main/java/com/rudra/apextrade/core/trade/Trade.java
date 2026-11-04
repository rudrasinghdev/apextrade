package com.rudra.apextrade.core.trade;

import com.rudra.apextrade.common.Side;

public record Trade(
        long tradeId,
        long makerOrderId,
        long takerOrderId,
        Side takerSide,
        long price,
        long quantity,
        long timestamp
) {}
