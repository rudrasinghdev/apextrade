package com.rudra.apextrade.common.wire;

import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderCommandCodecTest {

    private ByteBuffer buffer;

    record DecodedPlaceOrder(long orderId, long userId, Side side, long price, long quantity, OrderType orderType) {}
    record DecodedCancelOrder(long orderId, long userId) {}

    private final List<DecodedPlaceOrder> placeOrders = new ArrayList<>();
    private final List<DecodedCancelOrder> cancelOrders = new ArrayList<>();

    private final OrderCommandCodec.Handler testHandler = new OrderCommandCodec.Handler() {
        @Override
        public void onPlaceOrder(long orderId, long userId, Side side, long price, long quantity, OrderType orderType) {
            placeOrders.add(new DecodedPlaceOrder(orderId, userId, side, price, quantity, orderType));
        }

        @Override
        public void onCancelOrder(long orderId, long userId) {
            cancelOrders.add(new DecodedCancelOrder(orderId, userId));
        }
    };

    @BeforeEach
    void setUp() {
        buffer = ByteBuffer.allocateDirect(1024);
        placeOrders.clear();
        cancelOrders.clear();
    }

    @Test
    @DisplayName("Encode & Decode Single Place Order Frame (40 bytes)")
    void testEncodeDecodePlaceOrder() {
        OrderCommandCodec.encodePlaceOrder(buffer, 1001L, 501L, Side.BUY, 65_000L, 10L, OrderType.LIMIT);
        assertEquals(OrderCommandCodec.NEW_ORDER_FRAME_SIZE, buffer.position());

        buffer.flip();
        int decoded = OrderCommandCodec.decodeFrames(buffer, testHandler);

        assertEquals(1, decoded);
        assertEquals(0, buffer.remaining());
        assertEquals(1, placeOrders.size());

        DecodedPlaceOrder order = placeOrders.getFirst();
        assertEquals(1001L, order.orderId());
        assertEquals(501L, order.userId());
        assertEquals(Side.BUY, order.side());
        assertEquals(65_000L, order.price());
        assertEquals(10L, order.quantity());
        assertEquals(OrderType.LIMIT, order.orderType());
    }

    @Test
    @DisplayName("Encode & Decode Single Cancel Order Frame (20 bytes - 50% reduction)")
    void testEncodeDecodeCancelOrder() {
        OrderCommandCodec.encodeCancelOrder(buffer, 1002L, 501L);
        assertEquals(OrderCommandCodec.CANCEL_FRAME_SIZE, buffer.position());

        buffer.flip();
        int decoded = OrderCommandCodec.decodeFrames(buffer, testHandler);

        assertEquals(1, decoded);
        assertEquals(0, buffer.remaining());
        assertEquals(1, cancelOrders.size());

        DecodedCancelOrder cancel = cancelOrders.getFirst();
        assertEquals(1002L, cancel.orderId());
        assertEquals(501L, cancel.userId());
    }

    @Test
    @DisplayName("Interleaved Batch: [Place, Cancel, Place] variable sequence in a single buffer")
    void testInterleavedBatch() {
        OrderCommandCodec.encodePlaceOrder(buffer, 1L, 10L, Side.SELL, 50_000L, 5L, OrderType.LIMIT);
        OrderCommandCodec.encodeCancelOrder(buffer, 99L, 10L);
        OrderCommandCodec.encodePlaceOrder(buffer, 2L, 20L, Side.BUY, 50_000L, 5L, OrderType.MARKET);

        int expectedBytes = OrderCommandCodec.NEW_ORDER_FRAME_SIZE + OrderCommandCodec.CANCEL_FRAME_SIZE + OrderCommandCodec.NEW_ORDER_FRAME_SIZE;
        assertEquals(expectedBytes, buffer.position());

        buffer.flip();
        int decoded = OrderCommandCodec.decodeFrames(buffer, testHandler);

        assertEquals(3, decoded);
        assertEquals(2, placeOrders.size());
        assertEquals(1, cancelOrders.size());

        assertEquals(1L, placeOrders.get(0).orderId());
        assertEquals(99L, cancelOrders.get(0).orderId());
        assertEquals(2L, placeOrders.get(1).orderId());
        assertEquals(OrderType.MARKET, placeOrders.get(1).orderType());
    }

    @Test
    @DisplayName("Partial Frame Handling: Decoder safely rewinds when full command is not yet received")
    void testPartialFrameHandling() {
        // Encode a complete Place Order (40B) and only 10 bytes of a Cancel Order (needs 20B)
        OrderCommandCodec.encodePlaceOrder(buffer, 1L, 10L, Side.BUY, 50_000L, 1L, OrderType.LIMIT);

        ByteBuffer tempCancel = ByteBuffer.allocate(OrderCommandCodec.CANCEL_FRAME_SIZE);
        OrderCommandCodec.encodeCancelOrder(tempCancel, 2L, 10L);
        tempCancel.flip();

        byte[] partial = new byte[10];
        tempCancel.get(partial);
        buffer.put(partial);

        buffer.flip();

        int decoded = OrderCommandCodec.decodeFrames(buffer, testHandler);
        assertEquals(1, decoded);
        assertEquals(1, placeOrders.size());
        assertEquals(0, cancelOrders.size());
        assertEquals(10, buffer.remaining(), "10 partial bytes must remain unconsumed");

        // Complete the remaining 10 bytes
        buffer.compact();
        byte[] remaining = new byte[OrderCommandCodec.CANCEL_FRAME_SIZE - 10];
        tempCancel.get(remaining);
        buffer.put(remaining);
        buffer.flip();

        int secondDecoded = OrderCommandCodec.decodeFrames(buffer, testHandler);
        assertEquals(1, secondDecoded);
        assertEquals(1, cancelOrders.size());
        assertEquals(2L, cancelOrders.getFirst().orderId());
        assertEquals(0, buffer.remaining());
    }
}

