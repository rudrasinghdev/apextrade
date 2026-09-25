package com.rudra.apextrade.core.book;

import com.rudra.apextrade.core.order.Order;
import com.rudra.apextrade.core.order.PriceLevel;
import com.rudra.apextrade.core.order.Side;

import java.util.*;

public class OrderBook {

    private final String symbol;
    private final NavigableMap<Long, PriceLevel> bids;
    private final NavigableMap<Long, PriceLevel> asks;
    private final Map<Long, Order> orderMap;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderMap = new HashMap<>();
    }

    public void addOrder(Order order) {
        NavigableMap<Long, PriceLevel> book = getBook(order.getSide());
        PriceLevel level = book.computeIfAbsent(order.getPrice(), PriceLevel::new);
        level.append(order);
        orderMap.put(order.getOrderId(), order);
    }

    private NavigableMap<Long, PriceLevel> getBook(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public boolean cancelOrder(Long id) {
        Order order = orderMap.remove(id);
        if(order == null) {
            return false;
        }
        PriceLevel level = order.getParentLevel();
        if(level != null) {
            level.unlink(order);
            if(level.isEmpty()) {
                getBook(order.getSide()).remove(level.getPrice());
            }
        }
        order.cancel();
        return true;
    }

    public void removeFilledOrder(Order order) {
        PriceLevel level = order.getParentLevel();
        if (level != null) {
            level.unlink(order);
            if (level.isEmpty()) {
                getBook(order.getSide()).remove(level.getPrice());
            }
        }
        orderMap.remove(order.getOrderId());
    }

    public PriceLevel getBestBid() {
        Map.Entry<Long, PriceLevel> entry = bids.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    public PriceLevel getBestAsk() {
        Map.Entry<Long, PriceLevel> entry = asks.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    public Order getOrder(long orderId) {
        return orderMap.get(orderId);
    }

    public boolean hasBids() {
        return !bids.isEmpty();
    }

    public boolean hasAsks() {
        return !asks.isEmpty();
    }

}
