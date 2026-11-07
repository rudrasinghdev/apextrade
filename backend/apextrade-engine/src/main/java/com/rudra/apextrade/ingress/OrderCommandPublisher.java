package com.rudra.apextrade.ingress;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;

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
            event.set(CommandType.PLACE_ORDER, userId, orderId, side, price, quantity, orderType, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void publishCancelOrder(long orderId, long timestamp) {
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.set(CommandType.CANCEL_ORDER, 0, orderId, null, 0, 0, null, timestamp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
