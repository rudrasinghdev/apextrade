package com.rudra.apextrade.order.service;

import com.rudra.apextrade.account.exception.InsufficientFundsException;
import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.engine.order.OrderCommandPublisher;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
import com.rudra.apextrade.order.exception.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final OrderCommandPublisher publisher;
    private final AccountLedger ledger;
    private final AtomicLong orderIdSequence = new AtomicLong(1);

    public OrderService(OrderCommandPublisher publisher, AccountLedger ledger) {
        this.publisher = publisher;
        this.ledger = ledger;
    }

    public OrderResponse placeOrder(long userId, OrderRequest req) {
        long orderId = orderIdSequence.getAndIncrement();
        long timestampNs = System.nanoTime();

        if (req.side() == Side.BUY) {
            long requiredUsd = req.price() * req.quantity();
            boolean success = ledger.reserveBuyMargin(userId, orderId, requiredUsd);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient USD balance: required " + requiredUsd + " USD to place BUY order."
                );
            }
        } else if (req.side() == Side.SELL) {
            long requiredBtc = req.quantity();
            boolean success = ledger.reserveSellMargin(userId, orderId, requiredBtc);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient BTC balance: required " + requiredBtc + " BTC to place SELL order."
                );
            }
        }

        publisher.publishPlaceOrder(
                orderId,
                userId,
                req.side(),
                req.price(),
                req.quantity(),
                req.orderType(),
                timestampNs
        );

        return OrderResponse.accepted(orderId, timestampNs);
    }

    public OrderResponse cancelOrder(long userId, long orderId) {
        long timestampNs = System.nanoTime();

        if (!ledger.hasActiveOrder(userId, orderId)) {
            throw new OrderNotFoundException("Order #" + orderId + " not found or does not belong to trader #" + userId);
        }

        // Deterministic zero-race order cancellation:
        // Gateway dispatches CANCEL command to S2 Engine without prematurely unlocking margin.
        // Margin is unlocked exclusively upon receiving execution confirmation from Engine.
        publisher.publishCancelOrder(orderId, userId, timestampNs);
        return OrderResponse.accepted(orderId, timestampNs);
    }

    public Set<Long> getActiveOrderIds(long userId) {
        return ledger.getActiveOrderIds(userId);
    }
}
