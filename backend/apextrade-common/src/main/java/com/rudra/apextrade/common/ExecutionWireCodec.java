package com.rudra.apextrade.common;

import java.nio.ByteBuffer;

/**
 * High-performance, zero-allocation binary wire codec for execution events (Engine -> Gateway).
 *
 * Framing layout (72 bytes - 8-byte aligned for mechanical sympathy):
 * - Magic (2 bytes): 0x4558 ('EX')
 * - MsgType (1 byte): 1 = TRADE, 2 = CANCEL
 * - TakerSide (1 byte): 0 = BUY, 1 = SELL (indicates taker's side on trades)
 * - Reserved (4 bytes): Alignment padding
 * - TradeId (8 bytes): Trade ID (Trade) | 0L (Cancel)
 * - BuyerId (8 bytes): Buyer User ID (Trade / Buy Cancel) | 0L (Sell Cancel)
 * - BuyerOrderId (8 bytes): Buyer Order ID (Trade / Buy Cancel) | 0L (Sell Cancel)
 * - SellerId (8 bytes): Seller User ID (Trade / Sell Cancel) | 0L (Buy Cancel)
 * - SellerOrderId (8 bytes): Seller Order ID (Trade / Sell Cancel) | 0L (Buy Cancel)
 * - Price (8 bytes): Execution Price (Trade) | Order Limit Price (Cancel)
 * - Quantity (8 bytes): Match Quantity (Trade) | Remaining Quantity (Cancel)
 * - Timestamp (8 bytes): System.nanoTime()
 */
public final class ExecutionWireCodec {

    public static final short MAGIC = 0x4558; // 'EX'
    public static final int FRAME_SIZE = 72;
    public static final byte MSG_TYPE_TRADE = 1;
    public static final byte MSG_TYPE_CANCEL = 2;

    public interface Handler {
        void onTrade(long tradeId, long buyerId, long buyerOrderId,
                     long sellerId, long sellerOrderId, Side takerSide,
                     long price, long quantity, long timestamp);

        void onCancel(long buyerId, long buyerOrderId,
                      long sellerId, long sellerOrderId, Side side,
                      long price, long quantity, long timestamp);
    }

    private ExecutionWireCodec() {
    }

    public static void encodeTrade(
            ByteBuffer buffer,
            long tradeId,
            long buyerId,
            long buyerOrderId,
            long sellerId,
            long sellerOrderId,
            Side takerSide,
            long price,
            long quantity,
            long timestamp
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_TRADE);
        buffer.put((byte) (takerSide == Side.BUY ? 0 : 1));
        buffer.putInt(0);
        buffer.putLong(tradeId);
        buffer.putLong(buyerId);
        buffer.putLong(buyerOrderId);
        buffer.putLong(sellerId);
        buffer.putLong(sellerOrderId);
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(timestamp);
    }

    public static void encodeCancel(
            ByteBuffer buffer,
            long buyerId,
            long buyerOrderId,
            long sellerId,
            long sellerOrderId,
            Side side,
            long price,
            long quantity,
            long timestamp
    ) {
        buffer.putShort(MAGIC);
        buffer.put(MSG_TYPE_CANCEL);
        buffer.put((byte) (side == Side.BUY ? 0 : 1));
        buffer.putInt(0);
        buffer.putLong(0L); // tradeId = 0 for cancel
        buffer.putLong(buyerId);
        buffer.putLong(buyerOrderId);
        buffer.putLong(sellerId);
        buffer.putLong(sellerOrderId);
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(timestamp);
    }

    public static int decodeFrames(ByteBuffer buffer, Handler handler) {
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
            buffer.getInt(); // padding
            long tradeId = buffer.getLong();
            long buyerId = buffer.getLong();
            long buyerOrderId = buffer.getLong();
            long sellerId = buffer.getLong();
            long sellerOrderId = buffer.getLong();
            long price = buffer.getLong();
            long quantity = buffer.getLong();
            long timestamp = buffer.getLong();

            Side side = (sideByte == 0) ? Side.BUY : Side.SELL;

            if (msgType == MSG_TYPE_TRADE) {
                handler.onTrade(tradeId, buyerId, buyerOrderId, sellerId, sellerOrderId, side, price, quantity, timestamp);
            } else if (msgType == MSG_TYPE_CANCEL) {
                handler.onCancel(buyerId, buyerOrderId, sellerId, sellerOrderId, side, price, quantity, timestamp);
            }
            count++;
        }
        return count;
    }
}
