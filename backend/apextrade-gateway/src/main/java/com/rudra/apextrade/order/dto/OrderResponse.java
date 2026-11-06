package com.rudra.apextrade.order.dto;

public record OrderResponse(
        long orderId,
        String status,
        long timestampNs
) {
    public static OrderResponse accepted(long orderId, long timestampNs) {
        return new OrderResponse(orderId, "ACCEPTED", timestampNs);
    }
}

