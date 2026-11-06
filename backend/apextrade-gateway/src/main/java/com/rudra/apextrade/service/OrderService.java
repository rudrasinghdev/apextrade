package com.rudra.apextrade.service;

import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.dto.OrderRequest;
import com.rudra.apextrade.dto.OrderResponse;
import com.rudra.apextrade.exception.InsufficientFundsException;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import com.rudra.apextrade.risk.service.UserAccountLedger;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final OrderCommandPublisher publisher;
    private final UserAccountLedger ledger;
    private final AtomicLong orderIdSequence = new AtomicLong(1);

    private record ActiveReservation(long userId, Side side, long amount) {}
    private final ConcurrentHashMap<Long, ActiveReservation> activeReservations = new ConcurrentHashMap<>();

    public OrderService(OrderCommandPublisher publisher, UserAccountLedger ledger) {
        this.publisher = publisher;
        this.ledger = ledger;
    }

    public OrderResponse placeOrder(OrderRequest req) {
        long orderId = orderIdSequence.getAndIncrement();
        long timestampNs = System.nanoTime();

        if (req.side() == Side.BUY) {
            long requiredUsd = req.price() * req.quantity();
            boolean reserved = ledger.reserveUsd(req.userId(), requiredUsd);
            if (!reserved) {
                throw new InsufficientFundsException(
                        "Insufficient USD balance: required " + requiredUsd + " USD to place BUY order."
                );
            }
            if (requiredUsd > 0) {
                activeReservations.put(orderId, new ActiveReservation(req.userId(), Side.BUY, requiredUsd));
            }
        } else if (req.side() == Side.SELL) {
            long requiredBtc = req.quantity();
            boolean reserved = ledger.reserveBtc(req.userId(), requiredBtc);
            if (!reserved) {
                throw new InsufficientFundsException(
                        "Insufficient BTC balance: required " + requiredBtc + " BTC to place SELL order."
                );
            }
            if (requiredBtc > 0) {
                activeReservations.put(orderId, new ActiveReservation(req.userId(), Side.SELL, requiredBtc));
            }
        }

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

    public OrderResponse cancelOrder(long userId, long orderId) {
        long timestampNs = System.nanoTime();

        ActiveReservation res = activeReservations.remove(orderId);
        if (res != null) {
            if (res.side() == Side.BUY) {
                ledger.releaseUsd(res.userId(), res.amount());
            } else if (res.side() == Side.SELL) {
                ledger.releaseBtc(res.userId(), res.amount());
            }
        }

        publisher.publishCancelOrder(orderId, userId, timestampNs);

        return OrderResponse.accepted(orderId, timestampNs);
    }
}
