package com.rudra.apextrade.common;

public interface OrderWireHandler {
    void onNewOrder(long orderId, long userId, Side side, OrderType orderType, long price, long quantity);
    void onCancelOrder(long orderId, long userId);
}