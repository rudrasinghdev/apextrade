package com.rudra.apextrade.disruptor.publisher;

import com.lmax.disruptor.RingBuffer;
import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import com.rudra.apextrade.disruptor.event.CommandType;
import com.rudra.apextrade.disruptor.event.OrderCommandEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class DisruptorOrderPublisher {

    private final RingBuffer<OrderCommandEvent> ringBuffer;

    public DisruptorOrderPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public CompletableFuture<List<Trade>> publishPlaceOrder(long orderId, long userId, Side side,
                                                            long price, long quantity, OrderType orderType) {
        CompletableFuture<List<Trade>> future = new CompletableFuture<>();
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.set(CommandType.PLACE_ORDER, userId, orderId, side, price, quantity, orderType, future);
        } finally {
            ringBuffer.publish(sequence);
        }
        return future;
    }

    public CompletableFuture<List<Trade>> publishCancelOrder(long orderId, long userId) {
        CompletableFuture<List<Trade>> future = new CompletableFuture<>();
        long sequence = ringBuffer.next();
        try {
            OrderCommandEvent event = ringBuffer.get(sequence);
            event.setCancel(orderId, userId, future);
        } finally {
            ringBuffer.publish(sequence);
        }
        return future;
    }
}
