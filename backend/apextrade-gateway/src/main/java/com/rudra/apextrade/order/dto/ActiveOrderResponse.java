package com.rudra.apextrade.order.dto;

import com.rudra.apextrade.common.domain.Side;

public record ActiveOrderResponse(
        long orderId,
        Side side,
        String price,
        String initialQuantity,
        String remainingQuantity,
        boolean pendingCancel,
        long timestampNs
) {}