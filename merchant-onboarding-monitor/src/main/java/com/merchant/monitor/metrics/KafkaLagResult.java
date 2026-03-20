package com.merchant.monitor.metrics;

import org.apache.kafka.common.TopicPartition;

import java.util.Map;

public record KafkaLagResult(
        long totalLag,
        Map<TopicPartition, Long> lagByPartition
) {
}
