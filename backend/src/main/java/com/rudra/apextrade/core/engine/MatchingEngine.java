package com.rudra.apextrade.core.engine;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.order.Order;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.PriceLevel;
import com.rudra.apextrade.core.order.Side;
import lombok.Getter;

@Getter
public class MatchingEngine {

    private final OrderBook orderBook;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }

    public void processOrder(long userId, long orderId, Side side, long price,
                             long quantity, OrderType orderType, long timestamp) {

        long remainingQuantity = quantity;
        if (side == Side.BUY) {
            remainingQuantity = matchBuyOrder(price, remainingQuantity, orderType);
        } else {
            remainingQuantity = matchSellOrder(price, remainingQuantity, orderType);
        }

        if (remainingQuantity > 0 && orderType == OrderType.LIMIT) {
            Order restingOrder = new Order(orderId, userId, side, orderType, price, remainingQuantity, timestamp);
            orderBook.addOrder(restingOrder);
        }
    }

    private long matchBuyOrder(long price, long remainingQuantity, OrderType orderType) {
        while (remainingQuantity > 0 && orderBook.hasAsks()) {
            PriceLevel bestAsk = orderBook.getBestAsk();
            if (orderType == OrderType.LIMIT && bestAsk.getPrice() > price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(remainingQuantity, bestAsk);
        }
        return remainingQuantity;
    }

    private long matchSellOrder(long price, long remainingQuantity, OrderType orderType) {
        while (remainingQuantity > 0 && orderBook.hasBids()) {
            PriceLevel bestBids = orderBook.getBestBid();
            if (orderType == OrderType.LIMIT && bestBids.getPrice() < price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(remainingQuantity, bestBids);
        }
        return remainingQuantity;
    }

    private long matchAgainstLevel(long remainingQuantity, PriceLevel level) {
        while (remainingQuantity > 0 && !level.isEmpty()) {
            Order makerOrder = level.getFirstOrder();
            long matchQuantity = Math.min(makerOrder.getRemainingQuantity(), remainingQuantity);

            remainingQuantity -= matchQuantity;
            makerOrder.fill(matchQuantity);
            level.reduceVolume(matchQuantity);

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