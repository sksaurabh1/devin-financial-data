package com.merchant.monitor.metrics;

import com.merchant.monitor.config.MonitorConfig;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InfraCollector {

    private static final Logger log = LoggerFactory.getLogger(InfraCollector.class);

    private final MonitorConfig config;

    public InfraCollector(MonitorConfig config) {
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    public InfraResult collect() {
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);

            CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
            CoreV1Api coreV1Api = new CoreV1Api(client);

            String namespace = config.getKubernetes().getNamespace();
            String deployment = config.getKubernetes().getDeployment();
            String labelSelector = "app=" + deployment;

            Object metricsResponse = customObjectsApi.listNamespacedCustomObject(
                    "metrics.k8s.io", "v1beta1", namespace, "pods",
                    null, null, null, null, labelSelector,
                    null, null, null, null, null
            );

            long totalCpuNanocores = 0;
            long totalMemoryBytes = 0;

            Map<String, Object> metricsMap = (Map<String, Object>) metricsResponse;
            List<Map<String, Object>> items = (List<Map<String, Object>>) metricsMap.get("items");

            if (items != null) {
                for (Map<String, Object> item : items) {
                    List<Map<String, Object>> containers =
                            (List<Map<String, Object>>) item.get("containers");
                    if (containers != null) {
                        for (Map<String, Object> container : containers) {
                            Map<String, String> usage = (Map<String, String>) container.get("usage");
                            if (usage != null) {
                                totalCpuNanocores += parseCpuNanocores(usage.getOrDefault("cpu", "0"));
                                totalMemoryBytes += parseMemoryBytes(usage.getOrDefault("memory", "0"));
                            }
                        }
                    }
                }
            }

            long cpuLimitNanocores = 0;
            long memoryLimitBytes = 0;

            V1PodList podList = coreV1Api.listNamespacedPod(
                    namespace, null, null, null, null,
                    labelSelector, null, null, null, null, null, null
            );

            if (podList.getItems() != null) {
                for (V1Pod pod : podList.getItems()) {
                    if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                        for (V1Container container : pod.getSpec().getContainers()) {
                            V1ResourceRequirements resources = container.getResources();
                            if (resources != null && resources.getLimits() != null) {
                                if (resources.getLimits().containsKey("cpu")) {
                                    cpuLimitNanocores += parseCpuNanocores(
                                            resources.getLimits().get("cpu").toSuffixedString());
                                }
                                if (resources.getLimits().containsKey("memory")) {
                                    memoryLimitBytes += parseMemoryBytes(
                                            resources.getLimits().get("memory").toSuffixedString());
                                }
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
        } else {
            return Long.parseLong(memoryValue);
        }
    }
}
