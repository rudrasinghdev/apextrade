package com.rudra.apextrade.order.dto;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.order.exception.InvalidOrderException;

public record OrderRequest(
        Side side,
        long price,
        long quantity,
        OrderType orderType
) {
    public OrderRequest {
        if (side == null) {
            throw new InvalidOrderException("Side must be BUY or SELL");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }
        if (orderType == null) {
            throw new InvalidOrderException("OrderType must be LIMIT or MARKET");
        }
        if (orderType == OrderType.LIMIT && price <= 0) {
            throw new InvalidOrderException("Price must be greater than zero for LIMIT orders");
        }
    }
}

