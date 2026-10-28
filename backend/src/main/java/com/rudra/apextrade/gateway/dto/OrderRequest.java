package com.rudra.apextrade.gateway.dto;

import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;

public record OrderRequest(
        long userId,
        Side side,
        long price,
        long quantity,
        OrderType orderType
) {}
