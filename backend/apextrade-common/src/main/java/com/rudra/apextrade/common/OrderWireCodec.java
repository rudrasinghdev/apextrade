package com.rudra.apextrade.common;

import java.nio.ByteBuffer;

public final class OrderWireCodec {

    public static final short MAGIC = 0x4150;
    public static final int FRAME_SIZE = 40;

    public static final byte MSG_TYPE_NEW_ORDER = 1;
    public static final byte MSG_TYPE_CANCEL_ORDER = 2;

    private OrderWireCodec() {
    }

    public static void encodeNewOrder(
            ByteBuffer buffer,
            long orderId,
            long userId,
            Side side,
            OrderType orderType,
            long price,
            long quantity
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

    public static void encodeCancelOrder(ByteBuffer buffer, long orderId, long userId) {
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

    public static int decodeFrames(ByteBuffer buffer, OrderWireHandler handler) {
        int count = 0;
        while (buffer.remaining() >= FRAME_SIZE) {
            buffer.mark();
            short magic = buffer.getShort();
            if (magic != MAGIC) {
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

            Side side = (sideByte == 0) ? Side.BUY : Side.SELL;
            OrderType orderType = (orderTypeByte == 0) ? OrderType.LIMIT : OrderType.MARKET;

            if (msgType == MSG_TYPE_NEW_ORDER) {
                handler.onNewOrder(orderId, userId, side, orderType, price, quantity);
                count++;
            } else if (msgType == MSG_TYPE_CANCEL_ORDER) {
                handler.onCancelOrder(orderId, userId);
                count++;
            }
        }
        return count;
    }
}