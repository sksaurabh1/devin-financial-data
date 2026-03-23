package com.merchant.monitor.metrics;

import com.merchant.monitor.config.MonitorConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KafkaLagCollector {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagCollector.class);

    private final MonitorConfig config;

    public KafkaLagCollector(MonitorConfig config) {
        this.config = config;
    }

    public KafkaLagResult collect() {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getKafka().getBootstrapServers());

        AdminClient adminClient = null;
        try {
            adminClient = AdminClient.create(props);

            ListConsumerGroupOffsetsResult offsetsResult =
                    adminClient.listConsumerGroupOffsets(config.getKafka().getConsumerGroup());

            Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                    offsetsResult.partitionsToOffsetAndMetadata().get();

            if (committedOffsets == null || committedOffsets.isEmpty()) {
                return new KafkaLagResult(0, Collections.emptyMap());
            }

            Set<TopicPartition> partitions = committedOffsets.keySet().stream()
                    .filter(tp -> tp.topic().startsWith(config.getKafka().getTopicPrefix()))
                    .collect(Collectors.toSet());

            Map<TopicPartition, Long> endOffsets =
                    adminClient.listOffsets(
                            partitions.stream().collect(Collectors.toMap(
                                    tp -> tp,
                                    tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()
                            ))
                    ).all().get().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

            Map<TopicPartition, Long> lagByPartition = new HashMap<>();
            long totalLag = 0;

            for (TopicPartition tp : partitions) {
                long committed = committedOffsets.get(tp) != null
                        ? committedOffsets.get(tp).offset() : 0L;
                long end = endOffsets.getOrDefault(tp, 0L);
                long lag = Math.max(0, end - committed);
                lagByPartition.put(tp, lag);
                totalLag += lag;
            }

            return new KafkaLagResult(totalLag, lagByPartition);
        } catch (Exception e) {
            log.error("Failed to collect Kafka lag metrics", e);
            return new KafkaLagResult(-1, Collections.emptyMap());
        } finally {
            if (adminClient != null) {
                adminClient.close();
            }
        }
    }
}
