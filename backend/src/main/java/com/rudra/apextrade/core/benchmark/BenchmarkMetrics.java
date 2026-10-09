package com.rudra.apextrade.core.benchmark;

public record BenchmarkMetrics(
        int totalOrdersPushed,
        long elapsedNanos,
        double elapsedMillis,
        double ordersPerSecond,
        int poolAvailableAfter
) {}
