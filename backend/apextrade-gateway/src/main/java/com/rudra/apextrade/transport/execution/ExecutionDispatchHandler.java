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
        if (event.getExecType() == ExecutionType.TRADE) {
            handleTrade(event);
        } else if (event.getExecType() == ExecutionType.CANCELLED) {
            handleCancel(event);
        }
    }

    private void handleTrade(ExecutionEvent trade) {
        long usdCost = trade.getPrice() * trade.getQuantity();
        long btcQuantity = trade.getQuantity();

        ledger.settleBuyTrade(trade.getBuyerId(), trade.getBuyerOrderId(), usdCost, btcQuantity);
        ledger.settleSellTrade(trade.getSellerId(), trade.getSellerOrderId(), btcQuantity, usdCost);

        // Live WebSocket Broadcast to connected trading terminals
        String jsonPayload = String.format(
                "{\"type\":\"TRADE\",\"tradeId\":%d,\"buyerId\":%d,\"buyerOrderId\":%d,\"sellerId\":%d,\"sellerOrderId\":%d,\"takerSide\":\"%s\",\"price\":%d,\"quantity\":%d,\"timestamp\":%d}",
                trade.getTradeId(),
                trade.getBuyerId(),
                trade.getBuyerOrderId(),
                trade.getSellerId(),
                trade.getSellerOrderId(),
                trade.getTakerSide().name(),
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
                "{\"type\":\"CANCEL\",\"orderId\":%d,\"userId\":%d,\"price\":%d,\"quantity\":%d,\"timestamp\":%d}",
                orderId,
                userId,
                cancel.getPrice(),
                cancel.getQuantity(),
                cancel.getTimestamp()
        );
        webSocketHandler.broadcast(jsonPayload);
    }
}
