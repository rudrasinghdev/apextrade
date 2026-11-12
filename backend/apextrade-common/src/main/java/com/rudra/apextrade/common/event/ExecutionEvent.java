package com.rudra.apextrade.common.event;

import com.rudra.apextrade.common.domain.ExecutionType;
import com.rudra.apextrade.common.domain.Side;
import lombok.Getter;

abstract class ExecutionEventLhsPadding {
    protected long p1, p2, p3, p4;
}

@Getter
abstract class ExecutionEventFields extends ExecutionEventLhsPadding {
    protected ExecutionType execType;

    // Trade-specific fields
    protected long tradeId;
    protected long buyerUserId;
    protected long buyerOrderId;
    protected long sellerUserId;
    protected long sellerOrderId;

    // Cancel-specific fields
    protected long cancelledOrderId;
    protected long cancelledUserId;
    protected Side cancelledSide;

    // Shared primitive fields
    protected long price;
    protected long quantity; // matchQuantity on Trade, unfilledQuantity on Cancel
    protected long timestamp;
}

public class ExecutionEvent extends ExecutionEventFields {
    protected long p5, p6, p7, p8;

    public void setTrade(long tradeId, long buyerUserId, long buyerOrderId,
                         long sellerUserId, long sellerOrderId,
                         long price, long quantity, long timestamp) {
        this.execType = ExecutionType.TRADE;
        this.tradeId = tradeId;
        this.buyerUserId = buyerUserId;
        this.buyerOrderId = buyerOrderId;
        this.sellerUserId = sellerUserId;
        this.sellerOrderId = sellerOrderId;
        this.cancelledOrderId = 0L;
        this.cancelledUserId = 0L;
        this.cancelledSide = null;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public void setCancelled(long orderId, long userId, Side side,
                             long price, long unfilledQuantity, long timestamp) {
        this.execType = ExecutionType.CANCELLED;
        this.tradeId = 0L;
        this.buyerUserId = 0L;
        this.buyerOrderId = 0L;
        this.sellerUserId = 0L;
        this.sellerOrderId = 0L;
        this.cancelledOrderId = orderId;
        this.cancelledUserId = userId;
        this.cancelledSide = side;
        this.price = price;
        this.quantity = unfilledQuantity;
        this.timestamp = timestamp;
    }

    public void setCancelRejected(long orderId, long userId, long timestamp) {
        this.execType = ExecutionType.CANCEL_REJECTED;
        this.tradeId = 0L;
        this.buyerUserId = 0L;
        this.buyerOrderId = 0L;
        this.sellerUserId = 0L;
        this.sellerOrderId = 0L;
        this.cancelledOrderId = orderId;
        this.cancelledUserId = userId;
        this.cancelledSide = null;
        this.price = 0L;
        this.quantity = 0L;
        this.timestamp = timestamp;
    }
}
