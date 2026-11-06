package com.rudra.apextrade.order.service;

import com.rudra.apextrade.account.model.Account;
import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.account.exception.InsufficientFundsException;
import com.rudra.apextrade.order.exception.OrderNotFoundException;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
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

        Account account = ledger.getAccount(userId);

        // Pre-Trade Margin & Inventory Reservation (< 15 ns in RAM)
        if (req.side() == Side.BUY) {
            long requiredUsd = req.price() * req.quantity();
            boolean success = account.placeBuyOrder(orderId, requiredUsd);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient USD balance: required " + requiredUsd + " USD to place BUY order."
                );
            }
        } else if (req.side() == Side.SELL) {
            long requiredBtc = req.quantity();
            boolean success = account.placeSellOrder(orderId, requiredBtc);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient BTC balance: required " + requiredBtc + " BTC to place SELL order."
                );
            }
        }

        // Publish to MPSC Ingress RingBuffer
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

        Account account = ledger.getAccount(userId);
        boolean cancelled = account.cancelOrder(orderId);

        if (!cancelled) {
            throw new OrderNotFoundException("Order #" + orderId + " not found or does not belong to trader #" + userId);
        }

        publisher.publishCancelOrder(orderId, userId, timestampNs);

        return OrderResponse.accepted(orderId, timestampNs);
    }

    public Set<Long> getActiveOrderIds(long userId) {
        return ledger.getAccount(userId).getActiveOrderIds();
    }
}

