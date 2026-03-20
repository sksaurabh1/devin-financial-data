package com.merchant.monitor.report;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.MetricsResult;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;

@Component
public class ReportRenderer {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final MonitorConfig config;

    public ReportRenderer(MonitorConfig config) {
        this.config = config;
    }

    public String render(Report report) {
        MonitorConfig.Thresholds t = config.getThresholds();
        MetricsResult m = report.logMetrics();
        StringBuilder sb = new StringBuilder();

        sb.append("*:bar_chart: Merchant Onboarding Monitor Report*\n");
        sb.append("_Period: ").append(DISPLAY_FORMAT.format(report.periodStart()))
                .append(" — ").append(DISPLAY_FORMAT.format(report.periodEnd()))
                .append(" UTC_\n\n");

        sb.append("*Summary*\n");
        sb.append("| Metric | Value | Status |\n");
        sb.append("|--------|-------|--------|\n");

        sb.append("| Total Requests | ").append(m.totalRequests()).append(" | :white_check_mark: |\n");
        sb.append("| TPS | ").append(String.format("%.2f", m.tps())).append(" | :white_check_mark: |\n");

        sb.append("| Error% | ").append(String.format("%.2f%%", m.errorPercent())).append(" | ")
                .append(errorPercentEmoji(m.errorPercent(), t)).append(" |\n");

        sb.append("| Success Ratio | ").append(String.format("%.4f", m.successRatio())).append(" | :white_check_mark: |\n");

        sb.append("| P95 Latency | ").append(String.format("%.0fms", m.p95Ms())).append(" | ")
                .append(p95Emoji(m.p95Ms(), t)).append(" |\n");

        sb.append("| P99 Latency | ").append(String.format("%.0fms", m.p99Ms())).append(" | :white_check_mark: |\n");

        long kafkaLag = report.kafkaLag() != null ? report.kafkaLag().totalLag() : -1;
        sb.append("| Kafka Lag | ").append(kafkaLag).append(" | ")
                .append(kafkaLagEmoji(kafkaLag, t)).append(" |\n");

        double memUtil = report.infra() != null ? report.infra().memoryUtilPercent() : 0.0;
        sb.append("| Memory% | ").append(String.format("%.1f%%", memUtil)).append(" | ")
                .append(memoryEmoji(memUtil, t)).append(" |\n");

        double cpuUtil = report.infra() != null ? report.infra().cpuUtilPercent() : 0.0;
        sb.append("| CPU% | ").append(String.format("%.1f%%", cpuUtil)).append(" | ")
                .append(cpuEmoji(cpuUtil, t)).append(" |\n");

        sb.append("\n*Top 5 Endpoints by Traffic*\n");
        m.requestsByEndpoint().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> sb.append("• `").append(e.getKey()).append("` — ")
                        .append(e.getValue()).append(" requests\n"));

        sb.append("\n*Top 5 Endpoints by Error Rate*\n");
        m.errorRateByEndpoint().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .forEach(e -> sb.append("• `").append(e.getKey()).append("` — ")
                        .append(String.format("%.2f%%", e.getValue())).append("\n"));

        return sb.toString();
    }

    private String errorPercentEmoji(double errorPercent, MonitorConfig.Thresholds t) {
        if (errorPercent >= t.getErrorPercentCritical()) {
            return ":red_circle:";
        } else if (errorPercent >= t.getErrorPercentWarn()) {
            return ":warning:";
        }
        return ":white_check_mark:";
    }

    private String p95Emoji(double p95Ms, MonitorConfig.Thresholds t) {
        if (p95Ms >= t.getP95LatencyCriticalMs()) {
            return ":red_circle:";
        } else if (p95Ms >= t.getP95LatencyWarnMs()) {
            return ":warning:";
        }
        return ":white_check_mark:";
    }

    private String kafkaLagEmoji(long lag, MonitorConfig.Thresholds t) {
        if (lag < 0) {
            return ":question:";
        } else if (lag >= t.getKafkaLagCritical()) {
            return ":red_circle:";
        } else if (lag >= t.getKafkaLagWarn()) {
            return ":warning:";
        }
        return ":white_check_mark:";
    }

    private String memoryEmoji(double memUtil, MonitorConfig.Thresholds t) {
        if (memUtil >= t.getMemoryUtilWarn()) {
            return ":warning:";
        }
        return ":white_check_mark:";
    }

    private String cpuEmoji(double cpuUtil, MonitorConfig.Thresholds t) {
        if (cpuUtil >= t.getCpuUtilWarn()) {
            return ":warning:";
        }
        return ":white_check_mark:";
    }
}
