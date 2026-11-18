package com.rudra.apextrade.order.service;

import com.rudra.apextrade.account.exception.InsufficientFundsException;
import com.rudra.apextrade.account.model.ActiveOrder;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.order.dto.ActiveOrderResponse;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
import com.rudra.apextrade.order.exception.DuplicateCancelException;
import com.rudra.apextrade.order.exception.OrderNotFoundException;
import com.rudra.apextrade.transport.order.OrderCommandPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class OrderService {

    public static final long SATOSHI_SCALE = 100_000_000L;

    private final AtomicLong orderIdSequence = new AtomicLong(1);
    private final OrderCommandPublisher publisher;
    private final AccountService accountService;

    public static final int PRICE_SCALE = 2;
    public static final int QTY_SCALE   = 8;
    public static final long QTY_SCALE_FACTOR = 100_000_000L;

    public OrderService(OrderCommandPublisher publisher, AccountService accountService) {
        this.publisher = publisher;
        this.accountService = accountService;
    }

    public OrderResponse placeOrder(long userId, OrderRequest req) {
        long price = parse(req.price(), PRICE_SCALE);
        long quantity = parse(req.quantity(), QTY_SCALE);

        long orderId = orderIdSequence.getAndIncrement();
        long timestampNs = System.nanoTime();

        if (req.side() == Side.BUY) {
            boolean success = accountService.reserveBuyMargin(userId, orderId, price, quantity, timestampNs);
            if (!success) {
                long requiredUsdCents = Math.ceilDiv(Math.multiplyExact(price, quantity), SATOSHI_SCALE);
                throw new InsufficientFundsException(
                        "Insufficient USD balance: required " + requiredUsdCents + " cents to place BUY order.");
            }
        } else {
            boolean success = accountService.reserveSellMargin(userId, orderId, price, quantity, timestampNs);
            if (!success) {
                throw new InsufficientFundsException(
                        "Insufficient BTC balance: required " + quantity + " satoshis to place SELL order.");
            }
        }

        publisher.publishPlaceOrder(
                userId,
                orderId,
                req.side(),
                price,
                quantity,
                req.orderType(),
                timestampNs);

        log.info("[Gateway Ingress] Order #{} accepted for Trader #{} [{} {} sats @ {} cents]. Margin reserved. Published to Disruptor.",
                orderId, userId, req.side(), quantity, price);

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
                formatUsd(order.getPrice()),
                formatBtc(order.getInitialQuantity()),
                formatBtc(order.getRemainingQuantity()),
                order.isPendingCancel(),
                order.getTimestamp()
        );
    }

    private static String formatUsd(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(PRICE_SCALE).setScale(PRICE_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatBtc(long sats) {
        return BigDecimal.valueOf(sats).movePointLeft(QTY_SCALE).setScale(QTY_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static long parse(String val, int maxDecimals) {
        try {
            BigDecimal bd = new BigDecimal(val.trim());
            if (bd.scale() > maxDecimals) {
                throw new IllegalArgumentException("Exceeds max allowed decimal places (" + maxDecimals + "): " + val);
            }
            if (bd.signum() <= 0) {
                throw new IllegalArgumentException("Value must be strictly positive: " + val);
            }
            return bd.movePointRight(maxDecimals).longValueExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric string: " + val);
        }
    }
}
