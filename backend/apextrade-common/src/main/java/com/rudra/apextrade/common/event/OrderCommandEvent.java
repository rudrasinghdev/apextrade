package com.rudra.apextrade.common.event;

import com.rudra.apextrade.common.domain.CommandType;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import lombok.Getter;
import lombok.Setter;

abstract class OrderCommandEventLhsPadding {
    protected long p1, p2, p3, p4;
}

@Getter
@Setter
abstract class OrderCommandEventFields extends OrderCommandEventLhsPadding {
    protected CommandType commandType;
    protected long userId;
    protected long orderId;
    protected Side side;
    protected long price;
    protected long quantity;
    protected OrderType orderType;
    protected long timestamp;
}

public class OrderCommandEvent extends OrderCommandEventFields {
    protected long p5, p6, p7, p8;

    public void setPlaceOrder(long orderId, long userId, Side side,
                              long price, long quantity, OrderType orderType, long timestamp) {
        this.commandType = CommandType.PLACE_ORDER;
        this.orderId = orderId;
        this.userId = userId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
        this.timestamp = timestamp;
    }

    public void setCancelOrder(long orderId, long userId, long timestamp) {
        this.commandType = CommandType.CANCEL_ORDER;
        this.orderId = orderId;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    public void set(CommandType commandType, long userId, long orderId, Side side,
                    long price, long quantity, OrderType orderType, long timestamp) {
        this.commandType = commandType;
        this.userId = userId;
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
        this.timestamp = timestamp;
    }

    public void setCancel(long orderId, long userId) {
        this.commandType = CommandType.CANCEL_ORDER;
        this.orderId = orderId;
        this.userId = userId;
    }
}

