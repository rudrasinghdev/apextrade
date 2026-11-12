package com.rudra.apextrade.transport.execution;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.common.domain.ExecutionType;
import com.rudra.apextrade.common.event.ExecutionEvent;
import com.rudra.apextrade.websocket.TradeWebSocketHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes execution outcomes from S1 Execution Disruptor.
 * 1. On TRADE: Settles buyer and seller balances in AccountService without reverse-lookups.
 * 2. On CANCEL: Releases reserved margin in AccountService on Engine confirmation.
 * 3. Broadcasts live execution events to WebSocket subscribers on /ws/trades.
 */
@Slf4j
public class ExecutionDispatchHandler implements EventHandler<ExecutionEvent> {

    private final AccountService ledger;
    private final TradeWebSocketHandler webSocketHandler;

    public ExecutionDispatchHandler(
            AccountService ledger,
            TradeWebSocketHandler webSocketHandler
    ) {
        this.ledger = ledger;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void onEvent(ExecutionEvent event, long sequence, boolean endOfBatch) {
        try {
            if (event.getExecType() == ExecutionType.TRADE) {
                handleTrade(event);
            } else if (event.getExecType() == ExecutionType.CANCELLED) {
                handleCancel(event);
            } else if (event.getExecType() == ExecutionType.CANCEL_REJECTED) {
                handleCancelRejected(event);
            }
        } catch (Throwable t) {
            log.error("[Gateway Settlement] Unhandled exception processing execution event #{}: {}", sequence, t.getMessage(), t);
        }
    }

    private void handleTrade(ExecutionEvent trade) {
        long usdCost = trade.getPrice() * trade.getQuantity();
        long btcQuantity = trade.getQuantity();

        ledger.settleBuyTrade(trade.getBuyerUserId(), trade.getBuyerOrderId(), usdCost, btcQuantity);
        ledger.settleSellTrade(trade.getSellerUserId(), trade.getSellerOrderId(), btcQuantity, usdCost);

        log.info("[Gateway Settlement] Trade #{} settled in AccountService: Buyer #{} (-{} USD, +{} BTC) | Seller #{} (+{} USD, -{} BTC). Broadcasting to WebSocket /ws/trades.",
                trade.getTradeId(), trade.getBuyerUserId(), usdCost, btcQuantity, trade.getSellerUserId(), usdCost, btcQuantity);

        // Live WebSocket Broadcast to connected trading terminals
        String jsonPayload = String.format(
                "{\"type\":\"TRADE\",\"tradeId\":%d,\"buyerId\":%d,\"buyerOrderId\":%d,\"sellerId\":%d,\"sellerOrderId\":%d,\"price\":%d,\"quantity\":%d,\"timestamp\":%d}",
                trade.getTradeId(),
                trade.getBuyerUserId(),
                trade.getBuyerOrderId(),
                trade.getSellerUserId(),
                trade.getSellerOrderId(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getTimestamp()
        );
        webSocketHandler.broadcast(jsonPayload);
    }

    private void handleCancel(ExecutionEvent cancel) {
        long userId = cancel.getCancelledUserId();
        long orderId = cancel.getCancelledOrderId();

        log.info("[S1 Gateway Transport] Order #{} cancel confirmed by Engine. Unlocking margin for user #{}.", orderId, userId);

        ledger.cancelOrder(userId, orderId);

        String jsonPayload = String.format(
                "{\"type\":\"CANCEL\",\"orderId\":%d,\"userId\":%d,\"side\":\"%s\",\"price\":%d,\"quantity\":%d,\"timestamp\":%d}",
                orderId,
                userId,
                cancel.getCancelledSide() != null ? cancel.getCancelledSide().name() : "",
                cancel.getPrice(),
                cancel.getQuantity(),
                cancel.getTimestamp()
        );
        webSocketHandler.broadcast(jsonPayload);
    }

    private void handleCancelRejected(ExecutionEvent reject) {
        long userId = reject.getCancelledUserId();
        long orderId = reject.getCancelledOrderId();

        log.warn("[S1 Gateway Transport] Order #{} cancel REJECTED by Engine (already filled or not found). Trader #{}.", orderId, userId);

        // Reset the pending cancel fence so future cancels can be attempted if order is still active
        com.rudra.apextrade.account.model.ActiveOrder order = ledger.getActiveOrder(userId, orderId);
        if (order != null) {
            order.clearPendingCancel();
        }

        // Crucial: Margin is NOT unlocked because order was either filled or does not exist.
        String jsonPayload = String.format(
                "{\"type\":\"CANCEL_REJECTED\",\"orderId\":%d,\"userId\":%d,\"timestamp\":%d}",
                orderId,
                userId,
                reject.getTimestamp()
        );
        webSocketHandler.broadcast(jsonPayload);
    }
}
