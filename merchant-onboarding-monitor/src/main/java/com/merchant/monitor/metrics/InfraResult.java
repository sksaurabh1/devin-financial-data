package com.merchant.monitor.metrics;

public record InfraResult(
        double cpuUtilPercent,
        double memoryUtilPercent,
        long memoryUsedBytes,
        long memoryLimitBytes
) {
}
