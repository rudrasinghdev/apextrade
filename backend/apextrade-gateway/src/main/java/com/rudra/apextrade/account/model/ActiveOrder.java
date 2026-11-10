package com.rudra.apextrade.account.model;

import com.rudra.apextrade.common.Side;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

@Getter
public class ActiveOrder {

    private final long orderId;
    private final Side side;
    private final long price;
    private final long initialQuantity;
    private final AtomicLong remainingQuantity;
    private final long timestamp;

    public ActiveOrder(long orderId, Side side, long price, long quantity, long timestamp) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.initialQuantity = quantity;
        this.remainingQuantity = new AtomicLong(quantity);
        this.timestamp = timestamp;
    }

    public long getRemainingQuantity() {
        return remainingQuantity.get();
    }

    public long reduceQuantity(long amount) {
        return remainingQuantity.addAndGet(-amount);
    }
}

