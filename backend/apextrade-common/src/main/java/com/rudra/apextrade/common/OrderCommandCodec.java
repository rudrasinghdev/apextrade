package com.rudra.apextrade.common;

import java.nio.ByteBuffer;

public final class OrderCommandCodec {

    public static final short MAGIC = 0x4150; // 'AP'
    public static final int FRAME_SIZE = 40;
    public static final byte MSG_TYPE_NEW_ORDER = 1;
    public static final byte MSG_TYPE_CANCEL_ORDER = 2;

    private OrderCommandCodec() {
    }

    public static void encodeNewOrder(
            ByteBuffer buffer,
            long orderId,
            long userId,
            Side side,
            long price,
            long quantity,
            OrderType orderType
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_NEW_ORDER);
        buffer.put((byte) (side == Side.BUY ? 0 : 1));
        buffer.put((byte) (orderType == OrderType.LIMIT ? 0 : 1));
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        buffer.putLong(orderId);
        buffer.putLong(userId);
        buffer.putLong(price);
        buffer.putLong(quantity);
    }

    public static void encodeCancelOrder(
            ByteBuffer buffer,
            long orderId,
            long userId
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL_ORDER);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        buffer.putLong(orderId);
        buffer.putLong(userId);
        buffer.putLong(0L);
        buffer.putLong(0L);
    }
}

