package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.InfraResult;
import com.merchant.monitor.metrics.KafkaLagCollector;
import com.merchant.monitor.metrics.KafkaLagResult;
import com.merchant.monitor.metrics.LogMetricsCalculator;
import com.merchant.monitor.metrics.MetricsResult;
import com.merchant.monitor.notification.SlackNotifier;
import com.merchant.monitor.parser.ElfLogEntry;
import com.merchant.monitor.parser.ElfLogParser;
import com.merchant.monitor.report.Report;
import com.merchant.monitor.report.ReportRenderer;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ReportTool {

    private static final Logger log = LoggerFactory.getLogger(ReportTool.class);

    private final ElfLogParser parser;
    private final LogMetricsCalculator calculator;
    private final KafkaLagCollector kafkaCollector;
    private final InfraMonitorTool infraTool;
    private final ReportRenderer renderer;
    private final SlackNotifier notifier;
    private final MonitorConfig config;

    public ReportTool(ElfLogParser parser,
                      LogMetricsCalculator calculator,
                      KafkaLagCollector kafkaCollector,
                      InfraMonitorTool infraTool,
                      ReportRenderer renderer,
                      SlackNotifier notifier,
                      MonitorConfig config) {
        this.parser = parser;
        this.calculator = calculator;
        this.kafkaCollector = kafkaCollector;
        this.infraTool = infraTool;
        this.renderer = renderer;
        this.notifier = notifier;
        this.config = config;
    }

    @Tool("Generate a comprehensive Slack-formatted monitoring report with all metrics from the last 6 hours.")
    public String generateReport() {
        Report report = buildReport();
        return renderer.render(report);
    }

    @Tool("Generate and send a comprehensive monitoring report to the configured Slack channel.")
    public String generateAndSendReport() {
        try {
            Report report = buildReport();
            String markdown = renderer.render(report);
            notifier.send(markdown);
            return "Report generated and sent to Slack successfully.\n\n" + markdown;
        } catch (Exception e) {
            log.error("Failed to generate and send report", e);
            return "Failed to generate and send report: " + e.getMessage();
        }
    }

    private Report buildReport() {
        Instant to = Instant.now();
        Instant from = to.minus(6, ChronoUnit.HOURS);
        long windowSeconds = 21600L;

        List<ElfLogEntry> entries = parser.parse(config.getElf().getLogPath(), from, to);
        MetricsResult logMetrics = calculator.compute(entries, windowSeconds);
        KafkaLagResult kafkaLag = kafkaCollector.collect();
        InfraResult infra = infraTool.collectMetrics();

        return new Report(from, to, logMetrics, kafkaLag, infra);
    }
}
