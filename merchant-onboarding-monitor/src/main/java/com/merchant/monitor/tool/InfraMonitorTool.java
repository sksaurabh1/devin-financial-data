package com.merchant.monitor.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.InfraResult;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InfraMonitorTool {

    private static final Logger log = LoggerFactory.getLogger(InfraMonitorTool.class);

    private final MonitorConfig config;
    private final RestTemplate restTemplate;

    @Autowired
    public InfraMonitorTool(MonitorConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    InfraMonitorTool(MonitorConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Tool("Check K8s infrastructure metrics including CPU and memory utilization for merchant onboarding pods.")
    public String checkInfrastructure() {
        InfraResult result = collectMetrics();

        StringBuilder sb = new StringBuilder();
        sb.append("=== K8s Infrastructure Metrics ===\n");
        sb.append("CPU Utilization: ").append(String.format("%.1f%%", result.cpuUtilPercent())).append("\n");
        sb.append("Memory Utilization: ").append(String.format("%.1f%%", result.memoryUtilPercent())).append("\n");
        sb.append("Memory Used: ").append(formatBytes(result.memoryUsedBytes())).append("\n");
        sb.append("Memory Limit: ").append(formatBytes(result.memoryLimitBytes())).append("\n");

        return sb.toString();
    }

    public InfraResult collectMetrics() {
        try {
            String apiServer = config.getKubernetes().getApiServer();
            String namespace = config.getKubernetes().getNamespace();
            String deployment = config.getKubernetes().getDeployment();
            String labelSelector = "app=" + deployment;

            String metricsUrl = apiServer + "/apis/metrics.k8s.io/v1beta1/namespaces/"
                    + namespace + "/pods?labelSelector=" + labelSelector;
            JsonNode metricsResponse = restTemplate.getForObject(metricsUrl, JsonNode.class);

            long totalCpuNanocores = 0;
            long totalMemoryBytes = 0;

            if (metricsResponse != null && metricsResponse.has("items")) {
                for (JsonNode item : metricsResponse.get("items")) {
                    if (item.has("containers")) {
                        for (JsonNode container : item.get("containers")) {
                            JsonNode usage = container.get("usage");
                            if (usage != null) {
                                totalCpuNanocores += parseCpuNanocores(usage.path("cpu").asText("0"));
                                totalMemoryBytes += parseMemoryBytes(usage.path("memory").asText("0"));
                            }
                        }
                    }
                }
            }

            String podsUrl = apiServer + "/api/v1/namespaces/"
                    + namespace + "/pods?labelSelector=" + labelSelector;
            JsonNode podsResponse = restTemplate.getForObject(podsUrl, JsonNode.class);

            long cpuLimitNanocores = 0;
            long memoryLimitBytes = 0;

            if (podsResponse != null && podsResponse.has("items")) {
                for (JsonNode pod : podsResponse.get("items")) {
                    JsonNode containers = pod.path("spec").path("containers");
                    if (containers.isArray()) {
                        for (JsonNode container : containers) {
                            JsonNode limits = container.path("resources").path("limits");
                            if (!limits.isMissingNode()) {
                                cpuLimitNanocores += parseCpuNanocores(limits.path("cpu").asText("0"));
                                memoryLimitBytes += parseMemoryBytes(limits.path("memory").asText("0"));
                            }
                        }
                    }
                }
            }

            double cpuUtilPercent = cpuLimitNanocores > 0
                    ? (double) totalCpuNanocores / cpuLimitNanocores * 100.0 : 0.0;
            double memoryUtilPercent = memoryLimitBytes > 0
                    ? (double) totalMemoryBytes / memoryLimitBytes * 100.0 : 0.0;

            return new InfraResult(cpuUtilPercent, memoryUtilPercent, totalMemoryBytes, memoryLimitBytes);
        } catch (Exception e) {
            log.error("Failed to collect infrastructure metrics", e);
            return new InfraResult(0.0, 0.0, 0, 0);
        }
    }

    private long parseCpuNanocores(String cpuValue) {
        if (cpuValue.endsWith("n")) {
            return Long.parseLong(cpuValue.replace("n", ""));
        } else if (cpuValue.endsWith("m")) {
            return Long.parseLong(cpuValue.replace("m", "")) * 1_000_000L;
        } else {
            return (long) (Double.parseDouble(cpuValue) * 1_000_000_000L);
        }
    }

    private long parseMemoryBytes(String memoryValue) {
        if (memoryValue.endsWith("Ki")) {
            return Long.parseLong(memoryValue.replace("Ki", "")) * 1024L;
        } else if (memoryValue.endsWith("Mi")) {
            return Long.parseLong(memoryValue.replace("Mi", "")) * 1024L * 1024L;
        } else if (memoryValue.endsWith("Gi")) {
            return Long.parseLong(memoryValue.replace("Gi", "")) * 1024L * 1024L * 1024L;
        } else if (memoryValue.endsWith("Ti")) {
            return Long.parseLong(memoryValue.replace("Ti", "")) * 1024L * 1024L * 1024L * 1024L;
        } else if (memoryValue.endsWith("K") || memoryValue.endsWith("k")) {
            return Long.parseLong(memoryValue.substring(0, memoryValue.length() - 1)) * 1000L;
        } else if (memoryValue.endsWith("M")) {
            return Long.parseLong(memoryValue.substring(0, memoryValue.length() - 1)) * 1000_000L;
        } else if (memoryValue.endsWith("G")) {
            return Long.parseLong(memoryValue.substring(0, memoryValue.length() - 1)) * 1000_000_000L;
        } else if (memoryValue.endsWith("T")) {
            return Long.parseLong(memoryValue.substring(0, memoryValue.length() - 1)) * 1000_000_000_000L;
        } else {
            return Long.parseLong(memoryValue);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format("%.1f GiB", (double) bytes / (1024L * 1024L * 1024L));
        } else if (bytes >= 1024L * 1024L) {
            return String.format("%.1f MiB", (double) bytes / (1024L * 1024L));
        } else if (bytes >= 1024L) {
            return String.format("%.1f KiB", (double) bytes / 1024L);
        }
        return bytes + " B";
    }
}
