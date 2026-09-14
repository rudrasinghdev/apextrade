package com.rudra.apextrade.core.order;

import lombok.Getter;

@Getter
public class PriceLevel {

    private final long price;
    private long totalVolume;
    private int orderCount;

    private final Order head;
    private final Order tail;

    public PriceLevel(long price) {
        this.price = price;
        this.totalVolume = 0L;
        this.orderCount = 0;

        this.head = new Order(-1, -1, null, null, price, 0);
        this.tail = new Order(-1, -1, null, null, price, 0);

        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    public void append(Order order) {
        order.parentLevel = this;
        order.prev = tail.prev;
        order.prev.next = order;
        order.next = tail;
        tail.prev = order;

        this.totalVolume += order.getRemainingQuantity();
        orderCount++;
    }

    public void unlink(Order order) {
        order.prev.next = order.next;
        order.next.prev = order.prev;

        this.totalVolume -= order.getRemainingQuantity();
        orderCount--;

        order.prev = null;
        order.next = null;
        order.parentLevel = null;
    }

    public void reduceVolume(long filledQuantity) {
        this.totalVolume -= filledQuantity;
    }

    public Order getFirstOrder() {
        return isEmpty() ? null : head.next;
    }

    public boolean isEmpty() {
        return orderCount == 0;
    }

}
