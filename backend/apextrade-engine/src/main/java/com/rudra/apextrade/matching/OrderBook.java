package com.rudra.apextrade.matching;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.common.Side;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class OrderBook {

    private final String symbol;
    private final NavigableMap<Long, PriceLevel> bids;
    private final NavigableMap<Long, PriceLevel> asks;
    private final Map<Long, Order> orderMap;
    private final OrderPool orderPool;

    public OrderBook(String symbol, OrderPool orderPool) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderMap = new HashMap<>();
        this.orderPool = orderPool;
    }

    public void addOrder(long userId, long orderId, Side side, long price,
                         long quantity, OrderType orderType, long timestamp) {
        Order order = orderPool.borrow();
        if (order == null) {
            return;
        }
        order.set(userId, orderId, side, price, quantity, orderType, timestamp);
        NavigableMap<Long, PriceLevel> book = getBook(order.getSide());
        PriceLevel level = book.computeIfAbsent(order.getPrice(), PriceLevel::new);
        level.append(order);
        orderMap.put(order.getOrderId(), order);
    }

    private NavigableMap<Long, PriceLevel> getBook(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public Order cancelOrder(long id) {
        Order order = orderMap.remove(id);
        if (order == null) {
            return null;
        }
        PriceLevel level = order.getParentLevel();
        if (level != null) {
            level.unlink(order);
            if (level.isEmpty()) {
                getBook(order.getSide()).remove(level.getPrice());
            }
        }
        order.cancel();
        return order;
    }

    public void releaseOrder(Order order) {
        if (order != null) {
            orderPool.release(order);
        }
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
        orderPool.release(order);
    }

    public PriceLevel getBestBid() {
        Map.Entry<Long, PriceLevel> entry = bids.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    public PriceLevel getBestAsk() {
        Map.Entry<Long, PriceLevel> entry = asks.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    public boolean hasBids() {
        return !bids.isEmpty();
    }

    public boolean hasAsks() {
        return !asks.isEmpty();
    }
}

