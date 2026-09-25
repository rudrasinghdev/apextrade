package com.rudra.apextrade.core.engine;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.Order;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.PriceLevel;
import com.rudra.apextrade.core.order.Side;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MatchingEngine {

    private final OrderBook orderBook;
    private long nextTradeId = 1L;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }

    public List<Trade> processOrder(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        if(incomingOrder.getSide() == Side.BUY) {
            matchBuyOrder(incomingOrder, trades);
        } else {
            matchSellOrder(incomingOrder, trades);
        }
        if (!incomingOrder.isFilled() && incomingOrder.getType() == OrderType.LIMIT) {
            orderBook.addOrder(incomingOrder);
        }
        else if(!incomingOrder.isFilled() && incomingOrder.getType() == OrderType.MARKET) {
            incomingOrder.cancel();
        }
        return trades;
    }

    private void matchBuyOrder(Order buyOrder, List<Trade> trades) {
        while (buyOrder.getRemainingQuantity() > 0 && orderBook.hasAsks()) {
            PriceLevel bestAsk = orderBook.getBestAsk();
            if (buyOrder.getType() == OrderType.LIMIT && bestAsk.getPrice() > buyOrder.getPrice()) {
                break;
            }
            matchAgainstLevel(buyOrder, bestAsk, trades);
        }
    }

    private void matchSellOrder(Order sellOrder, List<Trade> trades) {
        while (sellOrder.getRemainingQuantity() > 0 && orderBook.hasBids()) {
            PriceLevel bestBids = orderBook.getBestBid();
            if(sellOrder.getType() == OrderType.LIMIT && bestBids.getPrice() < sellOrder.getPrice()) {
                break;
            }
            matchAgainstLevel(sellOrder, bestBids, trades);
        }
    }

    private void matchAgainstLevel(Order takerOrder, PriceLevel level, List<Trade> trades) {
        while (takerOrder.getRemainingQuantity() > 0 && !level.isEmpty()) {
            Order makerOrder = level.getFirstOrder();
            long matchQuantity = Math.min(makerOrder.getRemainingQuantity(), takerOrder.getRemainingQuantity());
            takerOrder.fill(matchQuantity);
            makerOrder.fill(matchQuantity);
            level.reduceVolume(matchQuantity);

            trades.add(new Trade(
                    nextTradeId++,
                    makerOrder.getOrderId(),
                    takerOrder.getOrderId(),
                    takerOrder.getSide(),
                    makerOrder.getPrice(),
                    matchQuantity,
                    System.nanoTime()
                    )
            );

            if (makerOrder.isFilled()) {
                orderBook.removeFilledOrder(makerOrder);
            }
        }
    }

    public boolean cancelOrder(long orderId) {
        return orderBook.cancelOrder(orderId);
    }

}
