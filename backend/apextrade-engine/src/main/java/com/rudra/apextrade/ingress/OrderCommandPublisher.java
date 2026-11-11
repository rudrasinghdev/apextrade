package com.rudra.apextrade.ingress;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.common.event.OrderCommandEvent;

public class OrderCommandPublisher {

    private final RingBuffer<OrderCommandEvent> ringBuffer;

    public OrderCommandPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void publishPlaceOrder(long orderId, long userId, Side side,
                                   long price, long quantity, OrderType orderType, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.setPlaceOrder(orderId, userId, side, price, quantity, orderType, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void publishCancelOrder(long orderId, long userId, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.setCancelOrder(orderId, userId, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
