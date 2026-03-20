package com.merchant.monitor.report;

import com.merchant.monitor.metrics.InfraResult;
import com.merchant.monitor.metrics.KafkaLagResult;
import com.merchant.monitor.metrics.MetricsResult;

import java.time.Instant;

public record Report(
        Instant periodStart,
        Instant periodEnd,
        MetricsResult logMetrics,
        KafkaLagResult kafkaLag,
        InfraResult infra
) {
}
