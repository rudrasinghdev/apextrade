package com.rudra.apextrade.ingress.event;

import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import lombok.Getter;
import lombok.Setter;

abstract class GatewayOrderCommandEventLhsPadding {
    protected long p1, p2, p3, p4;
}

@Getter
@Setter
abstract class GatewayOrderCommandEventFields extends GatewayOrderCommandEventLhsPadding {
    protected CommandType commandType;
    protected long userId;
    protected long orderId;
    protected Side side;
    protected long price;
    protected long quantity;
    protected OrderType orderType;
    protected long timestamp;
}

/**
 * Cache-line padded event for Gateway's MPSC Ingress Disruptor.
 */
public class OrderCommandEvent extends GatewayOrderCommandEventFields {
    protected long p5, p6, p7, p8;

    public void set(
            CommandType commandType,
            long userId,
            long orderId,
            Side side,
            long price,
            long quantity,
            OrderType orderType,
            long timestamp
    ) {
        this.commandType = commandType;
        this.userId = userId;
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
        this.timestamp = timestamp;
    }

    public void setCancel(long orderId, long userId, long timestamp) {
        this.commandType = CommandType.CANCEL_ORDER;
        this.orderId = orderId;
        this.userId = userId;
        this.timestamp = timestamp;
    }
}

