package com.rudra.apextrade.common.event;

import com.rudra.apextrade.common.ExecutionType;
import com.rudra.apextrade.common.Side;
import lombok.Getter;

abstract class ExecutionEventLhsPadding {
    protected long p1, p2, p3, p4;
}

@Getter
abstract class ExecutionEventFields extends ExecutionEventLhsPadding {
    protected ExecutionType execType;
    protected Side takerSide;
    protected long tradeId;
    protected long buyerId;
    protected long buyerOrderId;
    protected long sellerId;
    protected long sellerOrderId;
    protected long price;
    protected long quantity;
    protected long timestamp;
}

public class ExecutionEvent extends ExecutionEventFields {
    protected long p5, p6, p7, p8;

    public void setTrade(long tradeId, long buyerId, long buyerOrderId,
                         long sellerId, long sellerOrderId, Side takerSide,
                         long price, long quantity, long timestamp) {
        this.execType = ExecutionType.TRADE;
        this.takerSide = takerSide;
        this.tradeId = tradeId;
        this.buyerId = buyerId;
        this.buyerOrderId = buyerOrderId;
        this.sellerId = sellerId;
        this.sellerOrderId = sellerOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public void setCancel(long buyerId, long buyerOrderId,
                          long sellerId, long sellerOrderId, Side side,
                          long price, long quantity, long timestamp) {
        this.execType = ExecutionType.CANCELLED;
        this.takerSide = side;
        this.tradeId = 0L;
        this.buyerId = buyerId;
        this.buyerOrderId = buyerOrderId;
        this.sellerId = sellerId;
        this.sellerOrderId = sellerOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public long getCancelledUserId() {
        return buyerId != 0L ? buyerId : sellerId;
    }

    public long getCancelledOrderId() {
        return buyerOrderId != 0L ? buyerOrderId : sellerOrderId;
    }
}
