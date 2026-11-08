package com.rudra.apextrade.matching;

import com.rudra.apextrade.common.OrderStatus;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;
import lombok.Getter;

@Getter
public class Order {

    private long orderId;
    private long userId;
    private Side side;
    private OrderType type;
    private long price;
    private long initialQuantity;
    private long timestamp;
    private long remainingQuantity;
    private OrderStatus status;

    Order prev;
    Order next;
    PriceLevel parentLevel;

    public void set(long userId, long orderId, Side side, long price, long initialQuantity, OrderType type, long timestamp) {
        this.userId = userId;
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.type = type;
        this.timestamp = timestamp;
        this.remainingQuantity = initialQuantity;
        this.status = OrderStatus.NEW;
    }

    public void reset() {
        this.orderId = 0;
        this.userId = 0;
        this.side = null;
        this.type = null;
        this.price = 0;
        this.initialQuantity = 0;
        this.timestamp = 0;
        this.remainingQuantity = 0;
        this.status = null;
    }

    public void fill(long quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException();
        }
        this.remainingQuantity = remainingQuantity - quantity;
        this.status = (this.remainingQuantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }
}

