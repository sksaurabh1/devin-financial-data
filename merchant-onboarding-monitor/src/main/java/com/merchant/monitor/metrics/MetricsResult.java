package com.merchant.monitor.metrics;

import java.util.Map;

public record MetricsResult(
        long totalRequests,
        double tps,
        double errorPercent,
        double successRatio,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double avgLatencyMs,
        Map<String, Long> requestsByEndpoint,
        Map<String, Double> errorRateByEndpoint,
        Map<String, Long> requestsByHour
) {
}
