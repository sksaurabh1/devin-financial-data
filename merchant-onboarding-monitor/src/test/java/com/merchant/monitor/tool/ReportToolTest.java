package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.InfraResult;
import com.merchant.monitor.metrics.KafkaLagCollector;
import com.merchant.monitor.metrics.KafkaLagResult;
import com.merchant.monitor.metrics.LogMetricsCalculator;
import com.merchant.monitor.metrics.MetricsResult;
import com.merchant.monitor.notification.SlackNotifier;
import com.merchant.monitor.parser.ElfLogParser;
import com.merchant.monitor.report.Report;
import com.merchant.monitor.report.ReportRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportToolTest {

    @Mock
    private ElfLogParser parser;

    @Mock
    private LogMetricsCalculator calculator;

    @Mock
    private KafkaLagCollector kafkaCollector;

    @Mock
    private InfraMonitorTool infraTool;

    @Mock
    private ReportRenderer renderer;

    @Mock
    private SlackNotifier notifier;

    @Mock
    private MonitorConfig config;

    @Mock
    private MonitorConfig.Elf elfConfig;

    private ReportTool tool;

    @BeforeEach
    void setUp() {
        when(config.getElf()).thenReturn(elfConfig);
        when(elfConfig.getLogPath()).thenReturn("/var/log/test.log");
        tool = new ReportTool(parser, calculator, kafkaCollector, infraTool, renderer, notifier, config);
    }

    private void setupMetricsMocks() {
        MetricsResult logMetrics = new MetricsResult(
                500, 1.39, 2.0, 0.98,
                40.0, 150.0, 300.0, 80.0,
                Map.of("/api/merchants", 300L),
                Map.of("/api/merchants", 2.0),
                Map.of()
        );
        KafkaLagResult kafkaLag = new KafkaLagResult(100, Collections.emptyMap());
        InfraResult infra = new InfraResult(45.0, 60.0, 300_000_000L, 500_000_000L);

        when(parser.parse(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(calculator.compute(anyList(), anyLong())).thenReturn(logMetrics);
        when(kafkaCollector.collect()).thenReturn(kafkaLag);
        when(infraTool.collectMetrics()).thenReturn(infra);
    }

    @Test
    void generateReport_returnsRenderedReport() {
        setupMetricsMocks();
        when(renderer.render(any(Report.class))).thenReturn("*Monitoring Report*\nAll systems operational.");

        String result = tool.generateReport();

        assertThat(result).contains("Monitoring Report");
        verify(notifier, never()).send(anyString());
    }

    @Test
    void generateAndSendReport_sendsToSlack() {
        setupMetricsMocks();
        when(renderer.render(any(Report.class))).thenReturn("*Monitoring Report*\nAll systems operational.");

        String result = tool.generateAndSendReport();

        assertThat(result).contains("Report generated and sent to Slack successfully");
        assertThat(result).contains("Monitoring Report");
        verify(notifier).send("*Monitoring Report*\nAll systems operational.");
    }
}
