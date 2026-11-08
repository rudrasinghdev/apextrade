package com.rudra.apextrade.common;

import java.nio.ByteBuffer;

/**
 * High-performance, zero-allocation binary wire codec for executed trades (Engine -> Gateway).
 *
 * Framing layout (56 bytes - 8-byte aligned for mechanical sympathy):
 * - Magic (2 bytes): 0x5452 ('TR')
 * - MsgType (1 byte): 1 = TRADE_EXECUTION
 * - TakerSide (1 byte): 0 = BUY, 1 = SELL
 * - Reserved (4 bytes): Alignment padding
 * - TradeId (8 bytes): long
 * - MakerOrderId (8 bytes): long
 * - TakerOrderId (8 bytes): long
 * - Price (8 bytes): long
 * - Quantity (8 bytes): long
 * - Timestamp (8 bytes): long
 */
public final class TradeWireCodec {

    public static final short MAGIC = 0x5452;
    public static final int FRAME_SIZE = 56;
    public static final byte MSG_TYPE_TRADE = 1;

    public interface Handler {
        void onTrade(long tradeId, long makerOrderId, long takerOrderId,
                     Side takerSide, long price, long quantity, long timestamp);
    }

    private TradeWireCodec() {
    }

    public static void encodeTrade(
            ByteBuffer buffer,
            long tradeId,
            long makerOrderId,
            long takerOrderId,
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
        buffer.putLong(makerOrderId);
        buffer.putLong(takerOrderId);
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

            buffer.get(); // msgType
            byte sideByte = buffer.get();
            buffer.getInt(); // padding
            long tradeId = buffer.getLong();
            long makerOrderId = buffer.getLong();
            long takerOrderId = buffer.getLong();
            long price = buffer.getLong();
            long quantity = buffer.getLong();
            long timestamp = buffer.getLong();

            Side takerSide = (sideByte == 0) ? Side.BUY : Side.SELL;
            handler.onTrade(tradeId, makerOrderId, takerOrderId, takerSide, price, quantity, timestamp);
            count++;
        }
        return count;
    }
}