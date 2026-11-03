package com.rudra.apextrade.core.engine;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.order.*;
import com.rudra.apextrade.core.trade.TradeListener;
import lombok.Getter;

@Getter
public class MatchingEngine {

    private final OrderBook orderBook;
    private volatile long lastTradedPrice;
    private final TradeListener tradeListener;
    private long nextTradeId = 1L;

    public MatchingEngine(OrderBook orderBook) {
        this(orderBook, 0L, null);
    }

    public MatchingEngine(OrderBook orderBook, long listingPrice) {
        this(orderBook, listingPrice, null);
    }

    public MatchingEngine(OrderBook orderBook, long listingPrice, TradeListener tradeListener) {
        this.orderBook = orderBook;
        this.lastTradedPrice = listingPrice;
        this.tradeListener = tradeListener;
    }

    public void processOrder(long userId, long orderId, Side side, long price,
                             long quantity, OrderType orderType, long timestamp) {

        long remainingQuantity = quantity;
        if (side == Side.BUY) {
            remainingQuantity = matchBuyOrder(orderId, side, price, remainingQuantity, orderType, timestamp);
        } else {
            remainingQuantity = matchSellOrder(orderId, side, price, remainingQuantity, orderType, timestamp);
        }

        if (remainingQuantity > 0 && orderType == OrderType.LIMIT) {
            orderBook.addOrder(userId, orderId, side, price, remainingQuantity, orderType, timestamp);
        }
    }

    private long matchBuyOrder(long takerOrderId, Side takerSide, long price, long remainingQuantity, OrderType orderType, long timestamp) {
        while (remainingQuantity > 0 && orderBook.hasAsks()) {
            PriceLevel bestAsk = orderBook.getBestAsk();
            if (orderType == OrderType.LIMIT && bestAsk.getPrice() > price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(takerOrderId, takerSide, remainingQuantity, bestAsk, timestamp);
        }
        return remainingQuantity;
    }

    private long matchSellOrder(long takerOrderId, Side takerSide, long price, long remainingQuantity, OrderType orderType, long timestamp) {
        while (remainingQuantity > 0 && orderBook.hasBids()) {
            PriceLevel bestBids = orderBook.getBestBid();
            if (orderType == OrderType.LIMIT && bestBids.getPrice() < price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(takerOrderId, takerSide, remainingQuantity, bestBids, timestamp);
        }
        return remainingQuantity;
    }

    private long matchAgainstLevel(long takerOrderId, Side takerSide, long remainingQuantity, PriceLevel level, long timestamp) {
        long currentLevelPrice = level.getPrice();
        while (remainingQuantity > 0 && !level.isEmpty()) {
            Order makerOrder = level.getFirstOrder();
            long matchQuantity = Math.min(makerOrder.getRemainingQuantity(), remainingQuantity);

            remainingQuantity -= matchQuantity;
            makerOrder.fill(matchQuantity);
            level.reduceVolume(matchQuantity);

            if (this.lastTradedPrice != currentLevelPrice) {
                this.lastTradedPrice = currentLevelPrice;
            }

            if (tradeListener != null) {
                tradeListener.onTrade(
                        nextTradeId++,
                        makerOrder.getOrderId(),
                        takerOrderId,
                        takerSide,
                        currentLevelPrice,
                        matchQuantity,
                        timestamp
                );
            }

            if (makerOrder.isFilled()) {
                orderBook.removeFilledOrder(makerOrder);
            }
        }
        return remainingQuantity;
    }

    public boolean cancelOrder(long orderId) {
        return orderBook.cancelOrder(orderId);
    }
}