package com.rudra.apextrade.gateway.dto;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;

public record OrderRequest(
        long userId,
        Side side,
        long price,
        long quantity,
        OrderType orderType
) {}
