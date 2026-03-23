package com.merchant.monitor.tool;

import com.merchant.monitor.metrics.KafkaLagCollector;
import com.merchant.monitor.metrics.KafkaLagResult;
import dev.langchain4j.agent.tool.Tool;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaLagTool {

    private final KafkaLagCollector collector;

    public KafkaLagTool(KafkaLagCollector collector) {
        this.collector = collector;
    }

    @Tool("Check Kafka consumer group lag for merchant onboarding topics. Returns total lag and per-partition breakdown.")
    public String checkKafkaLag() {
        KafkaLagResult result = collector.collect();

        if (result.totalLag() < 0) {
            return "Failed to collect Kafka lag metrics. Check connectivity to Kafka brokers.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Kafka Consumer Lag ===\n");
        sb.append("Total Lag: ").append(result.totalLag()).append(" messages\n");

        if (!result.lagByPartition().isEmpty()) {
            sb.append("\nPer-Partition Breakdown:\n");
            for (Map.Entry<TopicPartition, Long> entry : result.lagByPartition().entrySet()) {
                TopicPartition tp = entry.getKey();
                sb.append("  ").append(tp.topic()).append("-").append(tp.partition())
                        .append(": ").append(entry.getValue()).append(" messages\n");
            }
        } else {
            sb.append("No partitions found for the configured consumer group.\n");
        }

        return sb.toString();
    }
}
