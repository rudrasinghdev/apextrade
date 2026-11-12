package com.rudra.apextrade.order.dto;

import com.rudra.apextrade.common.domain.Side;

public record ActiveOrderResponse(
        long orderId,
        Side side,
        long price,
        long initialQuantity,
        long remainingQuantity,
        boolean pendingCancel,
        long timestampNs
) {
}
