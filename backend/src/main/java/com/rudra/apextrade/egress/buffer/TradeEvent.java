package com.rudra.apextrade.egress.buffer;

import com.rudra.apextrade.core.order.Side;
import lombok.Getter;

class TradeEventLhsPadding {
    protected long p1, p2, p3, p4;
}

@Getter
class TradeEventFields extends TradeEventLhsPadding {
    protected long tradeId;
    protected long makerOrderId;
    protected long takerOrderId;
    protected Side takerSide;
    protected long price;
    protected long quantity;
    protected long timestamp;
}

public class TradeEvent extends TradeEventFields {
    protected long p5, p6, p7, p8;

    public void set(long tradeId, long makerOrderId, long takerOrderId,
                    Side takerSide, long price, long quantity, long timestamp) {
        this.tradeId = tradeId;
        this.makerOrderId = makerOrderId;
        this.takerOrderId = takerOrderId;
        this.takerSide = takerSide;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }
}