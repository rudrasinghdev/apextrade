package com.rudra.apextrade.common.wire;

import com.rudra.apextrade.common.domain.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionWireCodecTest {

    private ByteBuffer buffer;

    record DecodedTrade(long tradeId, long buyerUserId, long buyerOrderId,
                        long sellerUserId, long sellerOrderId, long price,
                        long quantity, long timestamp) {}

    record DecodedCancel(long orderId, long userId, Side side,
                         long price, long unfilledQuantity, long timestamp) {}

    record DecodedCancelReject(long orderId, long userId, long timestamp) {}

    private final List<DecodedTrade> trades = new ArrayList<>();
    private final List<DecodedCancel> cancels = new ArrayList<>();
    private final List<DecodedCancelReject> cancelRejects = new ArrayList<>();

    private final ExecutionWireCodec.Handler testHandler = new ExecutionWireCodec.Handler() {
        @Override
        public void onTrade(long tradeId, long buyerUserId, long buyerOrderId,
                            long sellerUserId, long sellerOrderId,
                            long price, long quantity, long timestamp) {
            trades.add(new DecodedTrade(tradeId, buyerUserId, buyerOrderId, sellerUserId, sellerOrderId, price, quantity, timestamp));
        }

        @Override
        public void onCancelled(long orderId, long userId, Side side,
                                long price, long unfilledQuantity, long timestamp) {
            cancels.add(new DecodedCancel(orderId, userId, side, price, unfilledQuantity, timestamp));
        }

        @Override
        public void onCancelRejected(long orderId, long userId, long timestamp) {
            cancelRejects.add(new DecodedCancelReject(orderId, userId, timestamp));
        }
    };

    @BeforeEach
    void setUp() {
        buffer = ByteBuffer.allocateDirect(1024);
        trades.clear();
        cancels.clear();
        cancelRejects.clear();
    }

    @Test
    @DisplayName("Encode & Decode Single Trade Frame (72 bytes)")
    void testEncodeDecodeTrade() {
        ExecutionWireCodec.encodeTrade(buffer, 1L, 101L, 10L, 202L, 20L, 50_000L, 5L, 123456789L);
        assertEquals(ExecutionWireCodec.TRADE_FRAME_SIZE, buffer.position());

        buffer.flip();
        int decoded = ExecutionWireCodec.decodeFrames(buffer, testHandler);

        assertEquals(1, decoded);
        assertEquals(0, buffer.remaining(), "Buffer should be completely consumed");
        assertEquals(1, trades.size());

        DecodedTrade trade = trades.getFirst();
        assertEquals(1L, trade.tradeId());
        assertEquals(101L, trade.buyerUserId());
        assertEquals(10L, trade.buyerOrderId());
        assertEquals(202L, trade.sellerUserId());
        assertEquals(20L, trade.sellerOrderId());
        assertEquals(50_000L, trade.price());
        assertEquals(5L, trade.quantity());
        assertEquals(123456789L, trade.timestamp());
    }

    @Test
    @DisplayName("Encode & Decode Single Cancel Frame (48 bytes)")
    void testEncodeDecodeCancel() {
        ExecutionWireCodec.encodeCancel(buffer, 42L, 999L, Side.SELL, 60_000L, 15L, 987654321L);
        assertEquals(ExecutionWireCodec.CANCEL_FRAME_SIZE, buffer.position());

        buffer.flip();
        int decoded = ExecutionWireCodec.decodeFrames(buffer, testHandler);

        assertEquals(1, decoded);
        assertEquals(0, buffer.remaining());
        assertEquals(1, cancels.size());

        DecodedCancel cancel = cancels.getFirst();
        assertEquals(42L, cancel.orderId());
        assertEquals(999L, cancel.userId());
        assertEquals(Side.SELL, cancel.side());
        assertEquals(60_000L, cancel.price());
        assertEquals(15L, cancel.unfilledQuantity());
        assertEquals(987654321L, cancel.timestamp());
    }

    @Test
    @DisplayName("Encode & Decode Single Cancel Reject Frame (28 bytes)")
    void testEncodeDecodeCancelReject() {
        ExecutionWireCodec.encodeCancelReject(buffer, 888L, 777L, 1122334455L);
        assertEquals(ExecutionWireCodec.CANCEL_REJECT_FRAME_SIZE, buffer.position());

        buffer.flip();
        int decoded = ExecutionWireCodec.decodeFrames(buffer, testHandler);

        assertEquals(1, decoded);
        assertEquals(0, buffer.remaining());
        assertEquals(1, cancelRejects.size());

        DecodedCancelReject reject = cancelRejects.getFirst();
        assertEquals(888L, reject.orderId());
        assertEquals(777L, reject.userId());
        assertEquals(1122334455L, reject.timestamp());
    }

    @Test
    @DisplayName("Interleaved Mixed Batch: [Trade, Cancel, CancelReject, Trade] in a single buffer")
    void testInterleavedBatch() {
        ExecutionWireCodec.encodeTrade(buffer, 1L, 10L, 100L, 20L, 200L, 50000L, 1L, 1000L);
        ExecutionWireCodec.encodeCancel(buffer, 300L, 30L, Side.BUY, 49000L, 2L, 1001L);
        ExecutionWireCodec.encodeCancelReject(buffer, 400L, 30L, 1002L);
        ExecutionWireCodec.encodeTrade(buffer, 2L, 10L, 101L, 40L, 400L, 50000L, 3L, 1003L);

        int totalBytes = ExecutionWireCodec.TRADE_FRAME_SIZE
                + ExecutionWireCodec.CANCEL_FRAME_SIZE
                + ExecutionWireCodec.CANCEL_REJECT_FRAME_SIZE
                + ExecutionWireCodec.TRADE_FRAME_SIZE;
        assertEquals(totalBytes, buffer.position());

        buffer.flip();
        int decoded = ExecutionWireCodec.decodeFrames(buffer, testHandler);

        assertEquals(4, decoded);
        assertEquals(2, trades.size());
        assertEquals(1, cancels.size());
        assertEquals(1, cancelRejects.size());

        assertEquals(1L, trades.get(0).tradeId());
        assertEquals(300L, cancels.get(0).orderId());
        assertEquals(400L, cancelRejects.get(0).orderId());
        assertEquals(2L, trades.get(1).tradeId());
    }

    @Test
    @DisplayName("Partial Frame Handling: Decoder safely rewinds when full frame is not yet received")
    void testPartialFrameHandling() {
        // Encode a complete Trade (72 bytes) and a partial Cancel (only 20 of 48 bytes)
        ExecutionWireCodec.encodeTrade(buffer, 1L, 10L, 100L, 20L, 200L, 50000L, 1L, 1000L);
        
        ByteBuffer tempCancel = ByteBuffer.allocate(ExecutionWireCodec.CANCEL_FRAME_SIZE);
        ExecutionWireCodec.encodeCancel(tempCancel, 300L, 30L, Side.BUY, 49000L, 2L, 1001L);
        tempCancel.flip();

        // Write only 20 bytes of the cancel into main buffer
        byte[] partialBytes = new byte[20];
        tempCancel.get(partialBytes);
        buffer.put(partialBytes);

        buffer.flip();

        // Decode should process the Trade, but pause at the partial Cancel without throwing BufferUnderflowException
        int decoded = ExecutionWireCodec.decodeFrames(buffer, testHandler);
        assertEquals(1, decoded);
        assertEquals(1, trades.size());
        assertEquals(0, cancels.size());
        assertEquals(20, buffer.remaining(), "The 20 partial bytes must remain unconsumed");

        // Now simulate the remaining 28 bytes arriving over TCP:
        buffer.compact();
        byte[] remainingBytes = new byte[ExecutionWireCodec.CANCEL_FRAME_SIZE - 20];
        tempCancel.get(remainingBytes);
        buffer.put(remainingBytes);
        buffer.flip();

        // Second decode pass: the Cancel should now decode cleanly!
        int decodedSecond = ExecutionWireCodec.decodeFrames(buffer, testHandler);
        assertEquals(1, decodedSecond);
        assertEquals(1, cancels.size());
        assertEquals(300L, cancels.getFirst().orderId());
        assertEquals(0, buffer.remaining());
    }
}

