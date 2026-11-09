package com.rudra.apextrade.account.model;

import com.rudra.apextrade.common.Side;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

@Getter
public class ActiveOrder {

    private final long orderId;
    private final Side side;
    private final long price;
    private final long originalQuantity;
    private final AtomicLong remainingQuantity;
    private final long createdAtNs;

    public ActiveOrder(long orderId, Side side, long price, long quantity, long createdAtNs) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = new AtomicLong(quantity);
        this.createdAtNs = createdAtNs;
    }

    public long getRemainingQuantity() {
        return remainingQuantity.get();
    }

    public long reduceQuantity(long amount) {
        return remainingQuantity.addAndGet(-amount);
    }
}

