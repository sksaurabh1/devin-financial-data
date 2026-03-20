package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ThresholdTool {

    private final MonitorConfig config;

    public ThresholdTool(MonitorConfig config) {
        this.config = config;
    }

    @Tool("Get current monitoring threshold configuration values.")
    public String getThresholds() {
        MonitorConfig.Thresholds t = config.getThresholds();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Monitoring Thresholds ===\n");
        sb.append("Error % Warning: ").append(t.getErrorPercentWarn()).append("%\n");
        sb.append("Error % Critical: ").append(t.getErrorPercentCritical()).append("%\n");
        sb.append("P95 Latency Warning: ").append(t.getP95LatencyWarnMs()).append("ms\n");
        sb.append("P95 Latency Critical: ").append(t.getP95LatencyCriticalMs()).append("ms\n");
        sb.append("Kafka Lag Warning: ").append(t.getKafkaLagWarn()).append(" messages\n");
        sb.append("Kafka Lag Critical: ").append(t.getKafkaLagCritical()).append(" messages\n");
        sb.append("CPU Utilization Warning: ").append(t.getCpuUtilWarn()).append("%\n");
        sb.append("Memory Utilization Warning: ").append(t.getMemoryUtilWarn()).append("%\n");

        return sb.toString();
    }

    @Tool("Evaluate system health against configured thresholds. Returns status for each metric.")
    public String evaluateHealth(
            @P("Current error percentage") double errorPct,
            @P("Current P95 latency in milliseconds") double p95,
            @P("Current Kafka consumer lag") long kafkaLag,
            @P("Current CPU utilization percentage") double cpu,
            @P("Current memory utilization percentage") double mem) {
        MonitorConfig.Thresholds t = config.getThresholds();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Health Evaluation ===\n");

        sb.append("Error Rate (").append(String.format("%.2f%%", errorPct)).append("): ");
        if (errorPct >= t.getErrorPercentCritical()) {
            sb.append("CRITICAL\n");
        } else if (errorPct >= t.getErrorPercentWarn()) {
            sb.append("WARNING\n");
        } else {
            sb.append("OK\n");
        }

        sb.append("P95 Latency (").append(String.format("%.0fms", p95)).append("): ");
        if (p95 >= t.getP95LatencyCriticalMs()) {
            sb.append("CRITICAL\n");
        } else if (p95 >= t.getP95LatencyWarnMs()) {
            sb.append("WARNING\n");
        } else {
            sb.append("OK\n");
        }

        sb.append("Kafka Lag (").append(kafkaLag).append("): ");
        if (kafkaLag < 0) {
            sb.append("UNKNOWN\n");
        } else if (kafkaLag >= t.getKafkaLagCritical()) {
            sb.append("CRITICAL\n");
        } else if (kafkaLag >= t.getKafkaLagWarn()) {
            sb.append("WARNING\n");
        } else {
            sb.append("OK\n");
        }

        sb.append("CPU (").append(String.format("%.1f%%", cpu)).append("): ");
        if (cpu >= t.getCpuUtilWarn()) {
            sb.append("WARNING\n");
        } else {
            sb.append("OK\n");
        }

        sb.append("Memory (").append(String.format("%.1f%%", mem)).append("): ");
        if (mem >= t.getMemoryUtilWarn()) {
            sb.append("WARNING\n");
        } else {
            sb.append("OK\n");
        }

        boolean anyIssue = errorPct >= t.getErrorPercentWarn()
                || p95 >= t.getP95LatencyWarnMs()
                || kafkaLag < 0 || kafkaLag >= t.getKafkaLagWarn()
                || cpu >= t.getCpuUtilWarn()
                || mem >= t.getMemoryUtilWarn();

        sb.append("\nOverall Status: ").append(anyIssue ? "DEGRADED" : "HEALTHY").append("\n");

        return sb.toString();
    }
}
