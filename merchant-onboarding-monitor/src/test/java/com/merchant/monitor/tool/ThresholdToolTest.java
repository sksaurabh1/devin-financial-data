package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThresholdToolTest {

    @Mock
    private MonitorConfig config;

    private MonitorConfig.Thresholds thresholds;

    private ThresholdTool tool;

    @BeforeEach
    void setUp() {
        thresholds = new MonitorConfig.Thresholds();
        thresholds.setErrorPercentWarn(2.0);
        thresholds.setErrorPercentCritical(5.0);
        thresholds.setP95LatencyWarnMs(500);
        thresholds.setP95LatencyCriticalMs(1000);
        thresholds.setKafkaLagWarn(1000);
        thresholds.setKafkaLagCritical(5000);
        thresholds.setCpuUtilWarn(80.0);
        thresholds.setMemoryUtilWarn(75.0);

        when(config.getThresholds()).thenReturn(thresholds);
        tool = new ThresholdTool(config);
    }

    @Test
    void getThresholds_returnsFormattedConfig() {
        String result = tool.getThresholds();

        assertThat(result).contains("Monitoring Thresholds");
        assertThat(result).contains("Error % Warning: 2.0%");
        assertThat(result).contains("Error % Critical: 5.0%");
        assertThat(result).contains("P95 Latency Warning: 500ms");
        assertThat(result).contains("P95 Latency Critical: 1000ms");
        assertThat(result).contains("Kafka Lag Warning: 1000 messages");
        assertThat(result).contains("Kafka Lag Critical: 5000 messages");
        assertThat(result).contains("CPU Utilization Warning: 80.0%");
        assertThat(result).contains("Memory Utilization Warning: 75.0%");
    }

    @Test
    void evaluateHealth_allOk_returnsHealthy() {
        String result = tool.evaluateHealth(1.0, 200.0, 500, 50.0, 60.0);

        assertThat(result).contains("Health Evaluation");
        assertThat(result).contains("Error Rate (1.00%): OK");
        assertThat(result).contains("P95 Latency (200ms): OK");
        assertThat(result).contains("Kafka Lag (500): OK");
        assertThat(result).contains("CPU (50.0%): OK");
        assertThat(result).contains("Memory (60.0%): OK");
        assertThat(result).contains("Overall Status: HEALTHY");
    }

    @Test
    void evaluateHealth_withWarnings_returnsDegraded() {
        String result = tool.evaluateHealth(3.0, 600.0, 2000, 85.0, 80.0);

        assertThat(result).contains("Error Rate (3.00%): WARNING");
        assertThat(result).contains("P95 Latency (600ms): WARNING");
        assertThat(result).contains("Kafka Lag (2000): WARNING");
        assertThat(result).contains("CPU (85.0%): WARNING");
        assertThat(result).contains("Memory (80.0%): WARNING");
        assertThat(result).contains("Overall Status: DEGRADED");
    }

    @Test
    void evaluateHealth_withCritical_returnsCritical() {
        String result = tool.evaluateHealth(6.0, 1500.0, 6000, 50.0, 50.0);

        assertThat(result).contains("Error Rate (6.00%): CRITICAL");
        assertThat(result).contains("P95 Latency (1500ms): CRITICAL");
        assertThat(result).contains("Kafka Lag (6000): CRITICAL");
        assertThat(result).contains("Overall Status: DEGRADED");
    }

    @Test
    void evaluateHealth_unknownKafkaLag() {
        String result = tool.evaluateHealth(1.0, 200.0, -1, 50.0, 60.0);

        assertThat(result).contains("Kafka Lag (-1): UNKNOWN");
    }
}
