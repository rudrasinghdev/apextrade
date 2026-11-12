package com.rudra.apextrade.order.service;

import com.rudra.apextrade.account.exception.InsufficientFundsException;
import com.rudra.apextrade.account.model.ActiveOrder;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.transport.order.OrderCommandPublisher;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.order.dto.ActiveOrderResponse;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
import com.rudra.apextrade.order.exception.DuplicateCancelException;
import com.rudra.apextrade.order.exception.OrderNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class OrderService {

    private final AtomicLong orderIdSequence = new AtomicLong(1);
    private final OrderCommandPublisher publisher;
    private final AccountService accountService;

    public OrderService(OrderCommandPublisher publisher, AccountService accountService) {
        this.publisher = publisher;
        this.accountService = accountService;
    }

    public OrderResponse placeOrder(long userId, OrderRequest req) {
        long orderId = orderIdSequence.getAndIncrement();
        long timestampNs = System.nanoTime();

        if (req.side() == Side.BUY) {
            boolean success = accountService.reserveBuyMargin(userId, orderId, req.price(), req.quantity(),
                    timestampNs);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient USD balance: required " + req.price() * req.quantity() + " USD to place BUY order.");
            }
        } else {
            boolean success = accountService.reserveSellMargin(userId, orderId, req.price(), req.quantity(),
                    timestampNs);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient BTC balance: required " + req.quantity() + " BTC to place SELL order.");
            }
        }

        publisher.publishPlaceOrder(
                userId,
                orderId,
                req.side(),
                req.price(),
                req.quantity(),
                req.orderType(),
                timestampNs);

        log.info("[Gateway Ingress] Order #{} accepted for Trader #{} [{} {} @ {} USD]. Margin reserved. Published to Disruptor.",
                orderId, userId, req.side(), req.quantity(), req.price());

        return OrderResponse.accepted(orderId, timestampNs);
    }

    public OrderResponse cancelOrder(long userId, long orderId) {
        long timestampNs = System.nanoTime();

        ActiveOrder order = accountService.getActiveOrder(userId, orderId);
        if (order == null) {
            log.warn("[Gateway Ingress] Cancel rejected: Order #{} not found or does not belong to Trader #{}.", orderId, userId);
            throw new OrderNotFoundException(
                    "Order #" + orderId + " not found or does not belong to trader #" + userId);
        }

        if (!order.markPendingCancel()) {
            log.warn("[Gateway Ingress] Duplicate cancel rejected: Order #{} already has a cancel in flight for Trader #{}.", orderId, userId);
            throw new DuplicateCancelException(
                    "Cancel request is already in flight for Order #" + orderId);
        }

        publisher.publishCancelOrder(userId, orderId, timestampNs);
        log.info("[Gateway Ingress] Cancel request accepted for Order #{} (Trader #{}). Published to Disruptor for Engine cancellation.", orderId, userId);
        return OrderResponse.accepted(orderId, timestampNs);
    }

    public List<ActiveOrderResponse> getAllActiveOrders(long userId) {
        return accountService.getActiveOrders(userId).stream()
                .map(this::mapToActiveOrderResponse)
                .toList();
    }

    public ActiveOrderResponse getActiveOrderById(long userId, long orderId) {
        ActiveOrder order = accountService.getActiveOrder(userId, orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order #" + orderId + " not found or does not belong to trader #" + userId);
        }
        return mapToActiveOrderResponse(order);
    }

    private ActiveOrderResponse mapToActiveOrderResponse(ActiveOrder order) {
        return new ActiveOrderResponse(
                order.getOrderId(),
                order.getSide(),
                order.getPrice(),
                order.getInitialQuantity(),
                order.getRemainingQuantity(),
                order.isPendingCancel(),
                order.getTimestamp()
        );
    }
}
