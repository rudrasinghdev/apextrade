package com.rudra.apextrade.gateway.service;

import com.rudra.apextrade.disruptor.publisher.OrderCommandPublisher;
import com.rudra.apextrade.gateway.dto.OrderRequest;
import com.rudra.apextrade.gateway.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final OrderCommandPublisher publisher;
    private final AtomicLong orderIdSequence = new AtomicLong(1);

    public OrderService(OrderCommandPublisher publisher) {
        this.publisher = publisher;
    }

    public OrderResponse placeOrder(OrderRequest req) {
        long orderId = orderIdSequence.getAndIncrement();
        long timestampNs = System.nanoTime();

        publisher.publishPlaceOrder(
                orderId,
                req.userId(),
                req.side(),
                req.price(),
                req.quantity(),
                req.orderType(),
                timestampNs
        );

        return OrderResponse.accepted(orderId, timestampNs);
    }

    public OrderResponse cancelOrder(long orderId) {
        long timestampNs = System.nanoTime();

        publisher.publishCancelOrder(orderId, timestampNs);

        return OrderResponse.accepted(orderId, timestampNs);
    }
}
