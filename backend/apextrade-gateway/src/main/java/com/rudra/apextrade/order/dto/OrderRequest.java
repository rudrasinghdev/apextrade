package com.rudra.apextrade.order.dto;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
        @NotNull(message = "Side must be BUY or SELL")
        Side side,

        @Positive(message = "Price must be greater than zero")
        long price,

        @Positive(message = "Quantity must be greater than zero")
        long quantity,

        @NotNull(message = "OrderType must be LIMIT or MARKET")
        OrderType orderType
) {}

