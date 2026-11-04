package com.rudra.apextrade.common;

import java.nio.ByteBuffer;

/**
 * High-performance, zero-allocation binary wire codec for raw TCP IPC between Gateway and Engine.
 * Framing layout (36 bytes):
 * - Magic (2 bytes): 0x4150 ('AP')
 * - MsgType (1 byte): 1 = NEW_ORDER, 2 = CANCEL_ORDER
 * - Side (1 byte): 0 = BUY, 1 = SELL
 * - OrderType (1 byte): 0 = LIMIT, 1 = MARKET
 * - Reserved (3 bytes): Alignment padding
 * - OrderId (8 bytes): long
 * - UserId (4 bytes): int
 * - Price (8 bytes): long (fixed-point cents/ticks)
 * - Quantity (8 bytes): long
 */
public final class OrderWireCodec {

    public static final short MAGIC = 0x4150;
    public static final int FRAME_SIZE = 36;

    public static final byte MSG_TYPE_NEW_ORDER = 1;
    public static final byte MSG_TYPE_CANCEL_ORDER = 2;

    private OrderWireCodec() {
    }

    public static void encodeNewOrder(
            ByteBuffer buffer,
            long orderId,
            int userId,
            Side side,
            OrderType orderType,
            long price,
            long quantity
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_NEW_ORDER);
        buffer.put((byte) (side == Side.BUY ? 0 : 1));
        buffer.put((byte) (orderType == OrderType.LIMIT ? 0 : 1));
        buffer.put((byte) 0); // pad 1
        buffer.putShort((short) 0); // pad 2 & 3
        buffer.putLong(orderId);
        buffer.putInt(userId);
        buffer.putLong(price);
        buffer.putLong(quantity);
    }

    public static void encodeCancelOrder(ByteBuffer buffer, long orderId, int userId) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL_ORDER);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        buffer.putLong(orderId);
        buffer.putInt(userId);
        buffer.putLong(0L);
        buffer.putLong(0L);
    }
}

