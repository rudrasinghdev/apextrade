package com.rudra.apextrade.core.order;

import lombok.Getter;

public class OrderPool {

    public static final int DEFAULT_CAPACITY = 100_000;

    @Getter
    private final int capacity;
    private final Order[] stack;
    private int head;

    public OrderPool() {
        this(DEFAULT_CAPACITY);
    }

    public OrderPool(int capacity) {
        this.capacity = capacity;
        this.stack = new Order[capacity];
        this.head = -1;

        System.out.println("[OrderPool] Pre-allocating and warming " + capacity + " orders...");

        for (int i = 0; i < capacity; i++) {
            stack[++head] = new Order();
        }

        System.out.println("[OrderPool] Initialized successfully. Available: " + getAvailableCount() + " / " + capacity);
    }

    public Order borrow() {
        if (head < 0) {
            System.out.println("[OrderPool] Pool exhausted");
            return null;
        }
        return stack[head--];
    }

    public void release(Order order) {
        if (order == null) {
            return;
        }

        order.reset();

        if (head < capacity - 1) {
            stack[++head] = order;
        } else {
            System.out.println("[OrderPool] Pool already full");
        }
    }

    public int getAvailableCount() {
        return head + 1;
    }

    public boolean isEmpty() {
        return head < 0;
    }
}
