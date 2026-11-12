package com.rudra.apextrade.transport.order;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.common.event.OrderCommandEvent;

public class OrderCommandPublisher {

    private final RingBuffer<OrderCommandEvent> ringBuffer;

    public OrderCommandPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void publishPlaceOrder(
            long userId,
            long orderId,
            Side side,
            long price,
            long quantity,
            OrderType orderType,
            long timestamp) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.setPlaceOrder(userId, orderId, side, price, quantity, orderType, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void publishCancelOrder(
            long userId,
            long orderId,
            long timestamp) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.setCancelOrder(userId, orderId, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
