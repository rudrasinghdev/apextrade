package com.rudra.apextrade.common.wire;

import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;

import java.nio.ByteBuffer;

/**
 * High-performance, zero-allocation variable-frame binary wire codec for order
 * commands (Gateway -> Engine).
 * Implements clean 4-byte protocol header and payload-specific body layouts.
 *
 * Framing layout:
 * Common Header (4 bytes):
 * - Magic (2 bytes): 0x4150 ('AP')
 * - MsgType (1 byte): 1 = NEW_ORDER, 2 = CANCEL_ORDER
 * - Padding (1 byte): Alignment padding
 *
 * Payload:
 * 1. NEW_ORDER (36 bytes payload -> 40 bytes total frame):
 * - Side (1B), OrderType (1B), Padding (2B) -> aligns subsequent longs to
 * offset 8
 * - OrderId (8B), UserId (8B), Price (8B), Quantity (8B)
 *
 * 2. CANCEL_ORDER (16 bytes payload -> 20 bytes total frame):
 * - OrderId (8B), UserId (8B)
 */
public final class OrderCommandCodec {

    public static final short MAGIC = 0x4150; // 'AP'
    public static final int HEADER_SIZE = 4;
    public static final int NEW_ORDER_FRAME_SIZE = 40;
    public static final int CANCEL_FRAME_SIZE = 20;
    public static final byte MSG_TYPE_NEW_ORDER = 1;
    public static final byte MSG_TYPE_CANCEL_ORDER = 2;

    public interface Handler {
        void onPlaceOrder(long userId, long orderId, Side side, long price, long quantity, OrderType orderType);

        void onCancelOrder(long userId, long orderId);
    }

    private OrderCommandCodec() {
    }

    public static void encodePlaceOrder(
            ByteBuffer buffer,
            long userId,
            long orderId,
            Side side,
            long price,
            long quantity,
            OrderType orderType) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_NEW_ORDER);
        buffer.put((byte) 0); // header padding
        buffer.put((byte) (side == Side.BUY ? 0 : 1));
        buffer.put((byte) (orderType == OrderType.LIMIT ? 0 : 1));
        buffer.putShort((short) 0); // 2 bytes padding to align longs to offset 8
        buffer.putLong(userId);
        buffer.putLong(orderId);
        buffer.putLong(price);
        buffer.putLong(quantity);
    }

    public static void encodeCancelOrder(
            ByteBuffer buffer,
            long userId,
            long orderId) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL_ORDER);
        buffer.put((byte) 0); // header padding
        buffer.putLong(userId);
        buffer.putLong(orderId);
    }

    public static int decodeFrames(ByteBuffer buffer, Handler handler) {
        int count = 0;
        while (buffer.remaining() >= HEADER_SIZE) {
            buffer.mark();
            short magic = buffer.getShort();
            if (magic != MAGIC) {
                buffer.reset();
                buffer.get();
                continue;
            }

            byte msgType = buffer.get();
            buffer.get(); // header padding

            int requiredFrameSize;
            if (msgType == MSG_TYPE_NEW_ORDER) {
                requiredFrameSize = NEW_ORDER_FRAME_SIZE;
            } else if (msgType == MSG_TYPE_CANCEL_ORDER) {
                requiredFrameSize = CANCEL_FRAME_SIZE;
            } else {
                buffer.reset();
                buffer.get();
                continue;
            }

            if (buffer.remaining() < (requiredFrameSize - HEADER_SIZE)) {
                buffer.reset();
                break; // partial frame, wait for next socket read
            }

            if (msgType == MSG_TYPE_NEW_ORDER) {
                byte sideByte = buffer.get();
                byte orderTypeByte = buffer.get();
                buffer.getShort(); // 2 bytes padding
                long orderId = buffer.getLong();
                long userId = buffer.getLong();
                long price = buffer.getLong();
                long quantity = buffer.getLong();
                Side side = (sideByte == 0) ? Side.BUY : Side.SELL;
                OrderType orderType = (orderTypeByte == 0) ? OrderType.LIMIT : OrderType.MARKET;
                handler.onPlaceOrder(orderId, userId, side, price, quantity, orderType);
            } else if (msgType == MSG_TYPE_CANCEL_ORDER) {
                long orderId = buffer.getLong();
                long userId = buffer.getLong();
                handler.onCancelOrder(orderId, userId);
            }
            count++;
        }
        return count;
    }
}
