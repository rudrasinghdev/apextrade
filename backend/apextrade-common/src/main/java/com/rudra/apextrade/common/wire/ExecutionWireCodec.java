package com.rudra.apextrade.common.wire;

import com.rudra.apextrade.common.domain.Side;
import java.nio.ByteBuffer;

/**
 * High-performance, zero-allocation variable-frame binary wire codec for execution events (Engine -> Gateway).
 * Implements clean 4-byte protocol header and payload-specific body layouts.
 *
 * Framing layout:
 * Common Header (4 bytes):
 * - Magic (2 bytes): 0x4558 ('EX')
 * - MsgType (1 byte): 1 = TRADE, 2 = CANCEL_CONFIRMED, 3 = CANCEL_REJECTED
 * - Padding (1 byte): Alignment padding
 *
 * Payload:
 * 1. TRADE (68 bytes payload -> 72 bytes total frame):
 *    - Padding (4B) -> aligns subsequent longs to offset 8
 *    - TradeId (8B), BuyerUserId (8B), BuyerOrderId (8B), SellerUserId (8B), SellerOrderId (8B)
 *    - Price (8B), Quantity (8B), Timestamp (8B)
 *
 * 2. CANCEL_CONFIRMED (44 bytes payload -> 48 bytes total frame):
 *    - Side (1B), Padding (3B) -> aligns subsequent longs to offset 8
 *    - OrderId (8B), UserId (8B), Price (8B), UnfilledQuantity (8B), Timestamp (8B)
 *
 * 3. CANCEL_REJECTED (24 bytes payload -> 28 bytes total frame):
 *    - OrderId (8B), UserId (8B), Timestamp (8B)
 */
public final class ExecutionWireCodec {

    public static final short MAGIC = 0x4558; // 'EX'
    public static final int HEADER_SIZE = 4;
    public static final int TRADE_FRAME_SIZE = 72;
    public static final int CANCEL_FRAME_SIZE = 48;
    public static final int CANCEL_REJECT_FRAME_SIZE = 28;

    public static final byte MSG_TYPE_TRADE = 1;
    public static final byte MSG_TYPE_CANCEL = 2;
    public static final byte MSG_TYPE_CANCEL_REJECT = 3;

    public interface Handler {
        void onTrade(long tradeId, long buyerUserId, long buyerOrderId,
                     long sellerUserId, long sellerOrderId,
                     long price, long quantity, long timestamp);

        void onCancelled(long orderId, long userId, Side side,
                        long price, long unfilledQuantity, long timestamp);

        void onCancelRejected(long orderId, long userId, long timestamp);
    }

    private ExecutionWireCodec() {
    }

    public static void encodeTrade(
            ByteBuffer buffer,
            long tradeId,
            long buyerUserId,
            long buyerOrderId,
            long sellerUserId,
            long sellerOrderId,
            long price,
            long quantity,
            long timestamp
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_TRADE);
        buffer.put((byte) 0); // header padding
        buffer.putInt(0);     // 4 bytes body padding to align longs to offset 8
        buffer.putLong(tradeId);
        buffer.putLong(buyerUserId);
        buffer.putLong(buyerOrderId);
        buffer.putLong(sellerUserId);
        buffer.putLong(sellerOrderId);
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(timestamp);
    }

    public static void encodeCancel(
            ByteBuffer buffer,
            long orderId,
            long userId,
            Side side,
            long price,
            long unfilledQuantity,
            long timestamp
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL);
        buffer.put((byte) 0); // header padding
        buffer.put((byte) (side == Side.BUY ? 0 : 1));
        buffer.put((byte) 0); // body padding byte 1
        buffer.putShort((short) 0); // body padding bytes 2 & 3
        buffer.putLong(orderId);
        buffer.putLong(userId);
        buffer.putLong(price);
        buffer.putLong(unfilledQuantity);
        buffer.putLong(timestamp);
    }

    public static void encodeCancelReject(
            ByteBuffer buffer,
            long orderId,
            long userId,
            long timestamp
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL_REJECT);
        buffer.put((byte) 0); // header padding
        buffer.putLong(orderId);
        buffer.putLong(userId);
        buffer.putLong(timestamp);
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
            if (msgType == MSG_TYPE_TRADE) {
                requiredFrameSize = TRADE_FRAME_SIZE;
            } else if (msgType == MSG_TYPE_CANCEL) {
                requiredFrameSize = CANCEL_FRAME_SIZE;
            } else if (msgType == MSG_TYPE_CANCEL_REJECT) {
                requiredFrameSize = CANCEL_REJECT_FRAME_SIZE;
            } else {
                buffer.reset();
                buffer.get();
                continue;
            }

            if (buffer.remaining() < (requiredFrameSize - HEADER_SIZE)) {
                buffer.reset();
                break; // partial frame, wait for next socket read
            }

            if (msgType == MSG_TYPE_TRADE) {
                buffer.getInt(); // 4 bytes alignment padding
                long tradeId       = buffer.getLong();
                long buyerUserId   = buffer.getLong();
                long buyerOrderId  = buffer.getLong();
                long sellerUserId  = buffer.getLong();
                long sellerOrderId = buffer.getLong();
                long price         = buffer.getLong();
                long quantity      = buffer.getLong();
                long timestamp     = buffer.getLong();
                handler.onTrade(tradeId, buyerUserId, buyerOrderId, sellerUserId, sellerOrderId, price, quantity, timestamp);
            } else if (msgType == MSG_TYPE_CANCEL) {
                byte sideByte         = buffer.get();
                buffer.get();         // body padding 1
                buffer.getShort();    // body padding 2 & 3
                long orderId          = buffer.getLong();
                long userId           = buffer.getLong();
                long price            = buffer.getLong();
                long unfilledQuantity = buffer.getLong();
                long timestamp        = buffer.getLong();
                Side side             = (sideByte == 0) ? Side.BUY : Side.SELL;
                handler.onCancelled(orderId, userId, side, price, unfilledQuantity, timestamp);
            } else if (msgType == MSG_TYPE_CANCEL_REJECT) {
                long orderId   = buffer.getLong();
                long userId    = buffer.getLong();
                long timestamp = buffer.getLong();
                handler.onCancelRejected(orderId, userId, timestamp);
            }
            count++;
        }
        return count;
    }
}
