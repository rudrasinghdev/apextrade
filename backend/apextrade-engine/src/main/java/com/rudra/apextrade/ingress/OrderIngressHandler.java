package com.rudra.apextrade.ingress;

import com.rudra.apextrade.common.OrderCommandCodec;
import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;

import java.nio.ByteBuffer;

public class OrderIngressHandler {

    private final OrderCommandPublisher publisher;

    public OrderIngressHandler(OrderCommandPublisher publisher) {
        this.publisher = publisher;
    }

    public void handle(ByteBuffer buffer) {
        while (buffer.remaining() >= OrderCommandCodec.FRAME_SIZE) {
            buffer.mark();
            short magic = buffer.getShort();
            if (magic != OrderCommandCodec.MAGIC) {
                buffer.reset();
                buffer.get();
                continue;
            }

            byte msgType = buffer.get();
            byte sideByte = buffer.get();
            byte orderTypeByte = buffer.get();
            buffer.get();
            buffer.getShort();
            long orderId = buffer.getLong();
            long userId = buffer.getLong();
            long price = buffer.getLong();
            long quantity = buffer.getLong();

            if (msgType == OrderCommandCodec.MSG_TYPE_NEW_ORDER) {
                Side side = (sideByte == 0) ? Side.BUY : Side.SELL;
                OrderType orderType = (orderTypeByte == 0) ? OrderType.LIMIT : OrderType.MARKET;
                publisher.publishPlaceOrder(orderId, userId, side, price, quantity, orderType, System.nanoTime());
            } else if (msgType == OrderCommandCodec.MSG_TYPE_CANCEL_ORDER) {
                publisher.publishCancelOrder(orderId, userId, System.nanoTime());
            }
        }
    }
}
