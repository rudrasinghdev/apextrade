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
    public void onPlaceOrder(long orderId, long userId, Side side, long price, long quantity, OrderType orderType) {
        publisher.publishPlaceOrder(orderId, userId, side, price, quantity, orderType, System.nanoTime());
    }

    @Override
    public void onCancelOrder(long orderId, long userId) {
        publisher.publishCancelOrder(orderId, userId, System.nanoTime());
    }
}
