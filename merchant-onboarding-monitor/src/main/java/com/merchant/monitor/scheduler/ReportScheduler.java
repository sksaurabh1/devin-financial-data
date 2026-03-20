package com.merchant.monitor.scheduler;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.InfraCollector;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final MonitorConfig config;
    private final ElfLogParser elfLogParser;
    private final LogMetricsCalculator logMetricsCalculator;
    private final KafkaLagCollector kafkaLagCollector;
    private final InfraCollector infraCollector;
    private final ReportRenderer reportRenderer;
    private final SlackNotifier slackNotifier;

    public ReportScheduler(MonitorConfig config,
                           ElfLogParser elfLogParser,
                           LogMetricsCalculator logMetricsCalculator,
                           KafkaLagCollector kafkaLagCollector,
                           InfraCollector infraCollector,
                           ReportRenderer reportRenderer,
                           SlackNotifier slackNotifier) {
        this.config = config;
        this.elfLogParser = elfLogParser;
        this.logMetricsCalculator = logMetricsCalculator;
        this.kafkaLagCollector = kafkaLagCollector;
        this.infraCollector = infraCollector;
        this.reportRenderer = reportRenderer;
        this.slackNotifier = slackNotifier;
    }

    @Scheduled(cron = "${monitor.schedule.cron}")
    public void generateReport() {
        try {
            log.info("Starting scheduled report generation");

            Instant to = Instant.now();
            Instant from = to.minus(6, ChronoUnit.HOURS);
            long windowSeconds = 21600;

            List<ElfLogEntry> entries = elfLogParser.parse(config.getElf().getLogPath(), from, to);
            log.info("Parsed {} log entries for period {} to {}", entries.size(), from, to);

            MetricsResult logMetrics = logMetricsCalculator.compute(entries, windowSeconds);
            log.info("Computed log metrics: {} total requests, {:.2f} TPS", logMetrics.totalRequests(), logMetrics.tps());

            KafkaLagResult kafkaLag = kafkaLagCollector.collect();
            log.info("Collected Kafka lag: {} total lag", kafkaLag.totalLag());

            InfraResult infra = infraCollector.collect();
            log.info("Collected infra metrics: CPU={:.1f}%, Memory={:.1f}%",
                    infra.cpuUtilPercent(), infra.memoryUtilPercent());

            Report report = new Report(from, to, logMetrics, kafkaLag, infra);

            String markdown = reportRenderer.render(report);
            log.info("Report rendered successfully");

            slackNotifier.send(markdown);
            log.info("Report generation and notification completed successfully");
        } catch (Exception e) {
            log.error("Failed to generate report", e);
        }
    }
}
