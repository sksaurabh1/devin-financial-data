package com.merchant.monitor.metrics;

import com.merchant.monitor.parser.ElfLogEntry;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LogMetricsCalculator {

    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(ZoneOffset.UTC);

    public MetricsResult compute(List<ElfLogEntry> entries, long windowSeconds) {
        if (entries.isEmpty()) {
            return new MetricsResult(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        long totalRequests = entries.size();
        double tps = (double) totalRequests / windowSeconds;

        long errorCount = 0;
        long successCount = 0;
        long latencySum = 0;
        List<Long> latencies = new ArrayList<>(entries.size());

        Map<String, Long> requestsByEndpoint = new HashMap<>();
        Map<String, Long> errorsByEndpoint = new HashMap<>();
        Map<String, Long> requestsByHour = new HashMap<>();

        for (ElfLogEntry entry : entries) {
            int status = entry.statusCode();

            if (status >= 400) {
                errorCount++;
            }
            if (status >= 200 && status < 300) {
                successCount++;
            }

            latencies.add(entry.latencyMs());
            latencySum += entry.latencyMs();

            String uri = entry.uri();
            requestsByEndpoint.merge(uri, 1L, Long::sum);
            if (status >= 400) {
                errorsByEndpoint.merge(uri, 1L, Long::sum);
            }

            String hourKey = HOUR_FORMAT.format(entry.timestamp());
            requestsByHour.merge(hourKey, 1L, Long::sum);
        }

        double errorPercent = (double) errorCount / totalRequests * 100.0;
        double successRatio = (double) successCount / totalRequests;
        double avgLatencyMs = (double) latencySum / totalRequests;

        Collections.sort(latencies);
        double p50Ms = percentile(latencies, 0.50);
        double p95Ms = percentile(latencies, 0.95);
        double p99Ms = percentile(latencies, 0.99);

        Map<String, Double> errorRateByEndpoint = new HashMap<>();
        for (Map.Entry<String, Long> ep : requestsByEndpoint.entrySet()) {
            long epErrors = errorsByEndpoint.getOrDefault(ep.getKey(), 0L);
            errorRateByEndpoint.put(ep.getKey(), (double) epErrors / ep.getValue() * 100.0);
        }

        return new MetricsResult(totalRequests, tps, errorPercent, successRatio,
                p50Ms, p95Ms, p99Ms, avgLatencyMs,
                requestsByEndpoint, errorRateByEndpoint, requestsByHour);
    }

    private double percentile(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
}
