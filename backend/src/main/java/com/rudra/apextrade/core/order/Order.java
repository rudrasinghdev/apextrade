package com.rudra.apextrade.core.order;

import lombok.Getter;

@Getter
public class Order {

    private final long orderId;
    private final long userId;
    private final Side side;
    private final OrderType type;
    private final long price;
    private final long initialQuantity;
    private final long timestamp;

    private long remainingQuantity;
    private OrderStatus status;

    Order prev;
    Order next;
    PriceLevel parentLevel;

    public Order(long orderId, long userId, Side side, OrderType type, long price, long initialQuantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = initialQuantity;
        this.status = OrderStatus.NEW;
        this.timestamp = System.nanoTime();
    }

    public void fill(long quantity) {
        if(quantity<=0 || quantity>remainingQuantity) {
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
