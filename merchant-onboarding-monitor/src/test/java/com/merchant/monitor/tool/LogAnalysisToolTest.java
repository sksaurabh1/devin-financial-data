package com.merchant.monitor.tool;

import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.LogMetricsCalculator;
import com.merchant.monitor.metrics.MetricsResult;
import com.merchant.monitor.parser.ElfLogEntry;
import com.merchant.monitor.parser.ElfLogParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogAnalysisToolTest {

    @Mock
    private ElfLogParser parser;

    @Mock
    private LogMetricsCalculator calculator;

    @Mock
    private MonitorConfig config;

    @Mock
    private MonitorConfig.Elf elfConfig;

    private LogAnalysisTool tool;

    @BeforeEach
    void setUp() {
        when(config.getElf()).thenReturn(elfConfig);
        when(elfConfig.getLogPath()).thenReturn("/var/log/test.log");
        tool = new LogAnalysisTool(parser, calculator, config);
    }

    @Test
    void analyzeRecentLogs_returnsFormattedMetrics() {
        MetricsResult metrics = new MetricsResult(
                1000, 2.78, 1.5, 0.985,
                50.0, 200.0, 500.0, 100.0,
                Map.of("/api/merchants", 500L, "/api/status", 300L),
                Map.of("/api/merchants", 1.0, "/api/status", 2.0),
                Map.of("2024-01-01T10", 500L)
        );

        when(parser.parse(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(calculator.compute(anyList(), anyLong())).thenReturn(metrics);

        String result = tool.analyzeRecentLogs(6);

        assertThat(result).contains("Log Analysis (last 6 hours)");
        assertThat(result).contains("Total Requests: 1000");
        assertThat(result).contains("TPS: 2.78");
        assertThat(result).contains("Error Rate: 1.50%");
        assertThat(result).contains("P50 Latency: 50ms");
        assertThat(result).contains("P95 Latency: 200ms");
        assertThat(result).contains("P99 Latency: 500ms");
        assertThat(result).contains("/api/merchants");
    }

    @Test
    void getEndpointMetrics_returnsFilteredMetrics() {
        ElfLogEntry matchingEntry = new ElfLogEntry(
                "10.0.0.1", Instant.now(), "GET", "/api/merchants", 200, 1024, 50);
        ElfLogEntry otherEntry = new ElfLogEntry(
                "10.0.0.1", Instant.now(), "GET", "/api/status", 200, 512, 30);

        MetricsResult metrics = new MetricsResult(
                1, 0.01, 0.0, 1.0,
                50.0, 50.0, 50.0, 50.0,
                Map.of("/api/merchants", 1L),
                Map.of("/api/merchants", 0.0),
                Map.of()
        );

        when(parser.parse(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(matchingEntry, otherEntry));
        when(calculator.compute(anyList(), anyLong())).thenReturn(metrics);

        String result = tool.getEndpointMetrics("/api/merchants", 6);

        assertThat(result).contains("Endpoint Metrics: /api/merchants");
        assertThat(result).contains("Total Requests: 1");
        assertThat(result).contains("P95 Latency: 50ms");
    }

    @Test
    void getEndpointMetrics_noMatches_returnsMessage() {
        when(parser.parse(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        String result = tool.getEndpointMetrics("/api/nonexistent", 6);

        assertThat(result).contains("No requests found for endpoint '/api/nonexistent'");
    }
}
