package com.rudra.apextrade.ingress;

import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.common.wire.OrderCommandCodec;

import java.nio.ByteBuffer;

public class OrderIngressHandler implements OrderCommandCodec.Handler {

    private final OrderCommandPublisher publisher;

    public OrderIngressHandler(OrderCommandPublisher publisher) {
        this.publisher = publisher;
    }

    public void handle(ByteBuffer buffer) {
        OrderCommandCodec.decodeFrames(buffer, this);
    }

    @Override
    public void onPlaceOrder(long userId, long orderId, Side side, long price, long quantity, OrderType orderType) {
        System.out.printf("[Engine Ingress] Decoded 40B frame from TCP socket: Order #%d from Trader #%d [%s %d @ %d USD]. Enqueueing to S2 Ingress RingBuffer.%n",
                orderId, userId, side, quantity, price);
        publisher.publishPlaceOrder(userId, orderId, side, price, quantity, orderType, System.nanoTime());
    }

    @Override
    public void onCancelOrder(long userId, long orderId) {
        System.out.printf("[Engine Ingress] Decoded 20B frame from TCP socket: Cancel Order #%d from Trader #%d. Enqueueing to S2 Ingress RingBuffer.%n",
                orderId, userId);
        publisher.publishCancelOrder(userId, orderId, System.nanoTime());
    }
}
