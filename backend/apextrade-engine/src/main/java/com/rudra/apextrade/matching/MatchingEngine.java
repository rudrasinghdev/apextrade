package com.rudra.apextrade.matching;

import com.rudra.apextrade.common.wire.ExecutionWireCodec;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import lombok.Getter;

@Getter
public class MatchingEngine {

    private final OrderBook orderBook;
    private volatile long lastTradedPrice;
    private final ExecutionWireCodec.Handler executionListener;
    private long nextTradeId = 1L;

    public MatchingEngine(OrderBook orderBook) {
        this(orderBook, 0L, null);
    }

    public MatchingEngine(OrderBook orderBook, long listingPrice) {
        this(orderBook, listingPrice, null);
    }

    public MatchingEngine(OrderBook orderBook, long listingPrice, ExecutionWireCodec.Handler executionListener) {
        this.orderBook = orderBook;
        this.lastTradedPrice = listingPrice;
        this.executionListener = executionListener;
    }

    public void processOrder(long userId, long orderId, Side side, long price,
                             long quantity, OrderType orderType, long timestamp) {

        long remainingQuantity = quantity;
        if (side == Side.BUY) {
            remainingQuantity = matchBuyOrder(userId, orderId, side, price, remainingQuantity, orderType, timestamp);
        } else {
            remainingQuantity = matchSellOrder(userId, orderId, side, price, remainingQuantity, orderType, timestamp);
        }

        if (remainingQuantity > 0 && orderType == OrderType.LIMIT) {
            orderBook.addOrder(userId, orderId, side, price, remainingQuantity, orderType, timestamp);
            System.out.printf("[Matching Engine] Order #%d is now RESTING in %s book [%d units @ %d USD].%n",
                    orderId, side, remainingQuantity, price);
        }
    }

    private long matchBuyOrder(long takerUserId, long takerOrderId, Side takerSide, long price,
                               long remainingQuantity, OrderType orderType, long timestamp) {
        while (remainingQuantity > 0 && orderBook.hasAsks()) {
            PriceLevel bestAsk = orderBook.getBestAsk();
            if (orderType == OrderType.LIMIT && bestAsk.getPrice() > price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(takerUserId, takerOrderId, takerSide, remainingQuantity, bestAsk, timestamp);
        }
        return remainingQuantity;
    }

    private long matchSellOrder(long takerUserId, long takerOrderId, Side takerSide, long price,
                                long remainingQuantity, OrderType orderType, long timestamp) {
        while (remainingQuantity > 0 && orderBook.hasBids()) {
            PriceLevel bestBids = orderBook.getBestBid();
            if (orderType == OrderType.LIMIT && bestBids.getPrice() < price) {
                break;
            }
            remainingQuantity = matchAgainstLevel(takerUserId, takerOrderId, takerSide, remainingQuantity, bestBids, timestamp);
        }
        return remainingQuantity;
    }

    private long matchAgainstLevel(long takerUserId, long takerOrderId, Side takerSide,
                                   long remainingQuantity, PriceLevel level, long timestamp) {
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

            if (executionListener != null) {
                long buyerUserId;
                long buyerOrderId;
                long sellerUserId;
                long sellerOrderId;

                if (takerSide == Side.BUY) {
                    buyerUserId = takerUserId;
                    buyerOrderId = takerOrderId;
                    sellerUserId = makerOrder.getUserId();
                    sellerOrderId = makerOrder.getOrderId();
                } else {
                    buyerUserId = makerOrder.getUserId();
                    buyerOrderId = makerOrder.getOrderId();
                    sellerUserId = takerUserId;
                    sellerOrderId = takerOrderId;
                }

                System.out.printf("[Matching Engine] MATCH EXECUTED! Trade #%d: Buyer #%d (Order #%d) x Seller #%d (Order #%d): %d units @ %d USD.%n",
                        nextTradeId, buyerUserId, buyerOrderId, sellerUserId, sellerOrderId, matchQuantity, currentLevelPrice);

                executionListener.onTrade(
                        nextTradeId++,
                        buyerUserId,
                        buyerOrderId,
                        sellerUserId,
                        sellerOrderId,
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
        return cancelOrder(0L, orderId);
    }

    public boolean cancelOrder(long userId, long orderId) {
        Order order = orderBook.cancelOrder(orderId);
        if (order != null) {
            System.out.printf("[Matching Engine] Order #%d cancelled from %s book [unfilled: %d].%n",
                    orderId, order.getSide(), order.getRemainingQuantity());

            if (executionListener != null) {
                executionListener.onCancelled(
                        order.getOrderId(),
                        order.getUserId(),
                        order.getSide(),
                        order.getPrice(),
                        order.getRemainingQuantity(),
                        System.nanoTime()
                );
            }
            orderBook.releaseOrder(order);
            return true;
        }

        System.out.printf("[Matching Engine] CANCEL REJECTED: Order #%d (Trader #%d) not found in book.%n",
                orderId, userId);
        if (executionListener != null) {
            executionListener.onCancelRejected(orderId, userId, System.nanoTime());
        }
        return false;
    }
}
