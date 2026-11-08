package com.rudra.apextrade.ingress;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.OrderWireCodec;
import com.rudra.apextrade.common.Side;

public class OrderCommandPublisher implements OrderWireCodec.Handler {

    private final RingBuffer<OrderCommandEvent> ringBuffer;

    public OrderCommandPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onNewOrder(long orderId, long userId, Side side, OrderType orderType, long price, long quantity) {
        publishPlaceOrder(orderId, userId, side, price, quantity, orderType, System.nanoTime());
    }

    @Override
    public void onCancelOrder(long orderId, long userId) {
        publishCancelOrder(orderId, System.nanoTime());
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
