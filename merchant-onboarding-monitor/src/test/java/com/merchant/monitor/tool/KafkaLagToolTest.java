package com.merchant.monitor.tool;

import com.merchant.monitor.metrics.KafkaLagCollector;
import com.merchant.monitor.metrics.KafkaLagResult;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaLagToolTest {

    @Mock
    private KafkaLagCollector collector;

    private KafkaLagTool tool;

    @BeforeEach
    void setUp() {
        tool = new KafkaLagTool(collector);
    }

    @Test
    void checkKafkaLag_returnsFormattedLag() {
        TopicPartition tp0 = new TopicPartition("merchant-onboarding-events", 0);
        TopicPartition tp1 = new TopicPartition("merchant-onboarding-events", 1);
        KafkaLagResult result = new KafkaLagResult(150, Map.of(tp0, 100L, tp1, 50L));

        when(collector.collect()).thenReturn(result);

        String output = tool.checkKafkaLag();

        assertThat(output).contains("Kafka Consumer Lag");
        assertThat(output).contains("Total Lag: 150 messages");
        assertThat(output).contains("merchant-onboarding-events-0");
        assertThat(output).contains("merchant-onboarding-events-1");
    }

    @Test
    void checkKafkaLag_error_returnsFailureMessage() {
        KafkaLagResult result = new KafkaLagResult(-1, Collections.emptyMap());

        when(collector.collect()).thenReturn(result);

        String output = tool.checkKafkaLag();

        assertThat(output).contains("Failed to collect Kafka lag metrics");
    }

    @Test
    void checkKafkaLag_noPartitions_returnsEmptyMessage() {
        KafkaLagResult result = new KafkaLagResult(0, Collections.emptyMap());

        when(collector.collect()).thenReturn(result);

        String output = tool.checkKafkaLag();

        assertThat(output).contains("Total Lag: 0 messages");
        assertThat(output).contains("No partitions found");
    }
}
