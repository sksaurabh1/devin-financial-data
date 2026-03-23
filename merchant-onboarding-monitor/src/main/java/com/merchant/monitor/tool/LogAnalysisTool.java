package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.LogMetricsCalculator;
import com.merchant.monitor.metrics.MetricsResult;
import com.merchant.monitor.parser.ElfLogEntry;
import com.merchant.monitor.parser.ElfLogParser;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LogAnalysisTool {

    private final ElfLogParser parser;
    private final LogMetricsCalculator calculator;
    private final MonitorConfig config;

    public LogAnalysisTool(ElfLogParser parser, LogMetricsCalculator calculator, MonitorConfig config) {
        this.parser = parser;
        this.calculator = calculator;
        this.config = config;
    }

    @Tool("Analyze recent ELF access logs for the given time window. Returns TPS, error rate, latency percentiles, and per-endpoint breakdown.")
    public String analyzeRecentLogs(@P("Number of hours to look back") int hoursBack) {
        Instant to = Instant.now();
        Instant from = to.minus(hoursBack, ChronoUnit.HOURS);
        long windowSeconds = hoursBack * 3600L;

        List<ElfLogEntry> entries = parser.parse(config.getElf().getLogPath(), from, to);
        MetricsResult m = calculator.compute(entries, windowSeconds);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Log Analysis (last ").append(hoursBack).append(" hours) ===\n");
        sb.append("Total Requests: ").append(m.totalRequests()).append("\n");
        sb.append("TPS: ").append(String.format("%.2f", m.tps())).append("\n");
        sb.append("Error Rate: ").append(String.format("%.2f%%", m.errorPercent())).append("\n");
        sb.append("Success Ratio: ").append(String.format("%.4f", m.successRatio())).append("\n");
        sb.append("P50 Latency: ").append(String.format("%.0fms", m.p50Ms())).append("\n");
        sb.append("P95 Latency: ").append(String.format("%.0fms", m.p95Ms())).append("\n");
        sb.append("P99 Latency: ").append(String.format("%.0fms", m.p99Ms())).append("\n");
        sb.append("Avg Latency: ").append(String.format("%.0fms", m.avgLatencyMs())).append("\n");

        sb.append("\nTop Endpoints by Traffic:\n");
        m.requestsByEndpoint().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append("  ").append(e.getKey())
                        .append(" - ").append(e.getValue()).append(" requests\n"));

        sb.append("\nEndpoint Error Rates:\n");
        m.errorRateByEndpoint().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append("  ").append(e.getKey())
                        .append(" - ").append(String.format("%.2f%%", e.getValue())).append("\n"));

        return sb.toString();
    }

    @Tool("Get metrics for a specific endpoint path over the given time window.")
    public String getEndpointMetrics(
            @P("Endpoint path to analyze") String path,
            @P("Number of hours to look back") int hours) {
        Instant to = Instant.now();
        Instant from = to.minus(hours, ChronoUnit.HOURS);
        long windowSeconds = hours * 3600L;

        List<ElfLogEntry> entries = parser.parse(config.getElf().getLogPath(), from, to);
        List<ElfLogEntry> filtered = entries.stream()
                .filter(e -> e.uri().equals(path))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return "No requests found for endpoint '" + path + "' in the last " + hours + " hours.";
        }

        MetricsResult m = calculator.compute(filtered, windowSeconds);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Endpoint Metrics: ").append(path).append(" (last ").append(hours).append(" hours) ===\n");
        sb.append("Total Requests: ").append(m.totalRequests()).append("\n");
        sb.append("TPS: ").append(String.format("%.2f", m.tps())).append("\n");
        sb.append("Error Rate: ").append(String.format("%.2f%%", m.errorPercent())).append("\n");
        sb.append("P50 Latency: ").append(String.format("%.0fms", m.p50Ms())).append("\n");
        sb.append("P95 Latency: ").append(String.format("%.0fms", m.p95Ms())).append("\n");
        sb.append("P99 Latency: ").append(String.format("%.0fms", m.p99Ms())).append("\n");
        sb.append("Avg Latency: ").append(String.format("%.0fms", m.avgLatencyMs())).append("\n");

        return sb.toString();
    }
}
