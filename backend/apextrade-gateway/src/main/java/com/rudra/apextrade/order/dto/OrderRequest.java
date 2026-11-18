package com.rudra.apextrade.order.dto;

import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull(message = "Side must be BUY or SELL")
        Side side,

        @NotBlank(message = "Price is required")
        String price,

        @NotBlank(message = "Quantity must be greater than zero")
        String quantity,

        @NotNull(message = "OrderType must be LIMIT or MARKET")
        OrderType orderType
) {}
