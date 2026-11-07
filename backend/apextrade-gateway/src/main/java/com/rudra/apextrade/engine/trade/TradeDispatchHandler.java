package com.rudra.apextrade.engine.trade;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.common.Side;
import com.rudra.apextrade.order.service.OrderService;
import com.rudra.apextrade.websocket.TradeWebSocketHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes trades from S1 Trade Disruptor.
 * 1. Settles buyer and seller balances in AccountLedger.
 * 2. Broadcasts live JSON execution to WebSocket subscribers on /ws/trades.
 */
@Slf4j
public class TradeDispatchHandler implements EventHandler<TradeEvent> {

    private final AccountLedger ledger;
    private final OrderService orderService;
    private final TradeWebSocketHandler webSocketHandler;

    public TradeDispatchHandler(
            AccountLedger ledger,
            OrderService orderService,
            TradeWebSocketHandler webSocketHandler
    ) {
        this.ledger = ledger;
        this.orderService = orderService;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void onEvent(TradeEvent trade, long sequence, boolean endOfBatch) {
        long usdCost = trade.getPrice() * trade.getQuantity();
        long btcQuantity = trade.getQuantity();

        Long makerUserId = orderService.getOrderOwner(trade.getMakerOrderId());
        Long takerUserId = orderService.getOrderOwner(trade.getTakerOrderId());

        if (trade.getTakerSide() == Side.BUY) {
            // Taker is BUYING, Maker is SELLING
            if (takerUserId != null) {
                ledger.settleBuyTrade(takerUserId, trade.getTakerOrderId(), usdCost, btcQuantity);
            }
            if (makerUserId != null) {
                ledger.settleSellTrade(makerUserId, trade.getMakerOrderId(), btcQuantity, usdCost);
            }
        } else {
            // Taker is SELLING, Maker is BUYING
            if (takerUserId != null) {
                ledger.settleSellTrade(takerUserId, trade.getTakerOrderId(), btcQuantity, usdCost);
            }
            if (makerUserId != null) {
                ledger.settleBuyTrade(makerUserId, trade.getMakerOrderId(), usdCost, btcQuantity);
            }
        }

        // Live WebSocket Broadcast to connected trading terminals
        String jsonPayload = String.format(
                "{\"type\":\"TRADE\",\"tradeId\":%d,\"makerOrderId\":%d,\"takerOrderId\":%d,\"takerSide\":\"%s\",\"price\":%d,\"quantity\":%d,\"timestamp\":%d}",
                trade.getTradeId(),
                trade.getMakerOrderId(),
                trade.getTakerOrderId(),
                trade.getTakerSide().name(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getTimestamp()
        );
        webSocketHandler.broadcast(jsonPayload);
    }
}