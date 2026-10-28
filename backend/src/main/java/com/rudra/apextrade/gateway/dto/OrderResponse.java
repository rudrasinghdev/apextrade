package com.rudra.apextrade.gateway.dto;

public record OrderResponse(
        String status,
        long orderId,
        long timestampNs,
        String message
) {
    public static OrderResponse accepted(long orderId, long timestampNs) {
        return new OrderResponse("ACCEPTED", orderId, timestampNs, "Order queued for execution");
    }

    public static OrderResponse rejected(String message) {
        return new OrderResponse("REJECTED", -1, System.nanoTime(), message);
    }
}

