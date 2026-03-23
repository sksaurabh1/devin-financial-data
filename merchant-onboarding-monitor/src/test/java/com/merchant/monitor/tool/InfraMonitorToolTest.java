package com.merchant.monitor.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchant.monitor.config.MonitorConfig;
import com.merchant.monitor.metrics.InfraResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InfraMonitorToolTest {

    @Mock
    private MonitorConfig config;

    @Mock
    private MonitorConfig.Kubernetes k8sConfig;

    @Mock
    private RestTemplate restTemplate;

    private InfraMonitorTool tool;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(config.getKubernetes()).thenReturn(k8sConfig);
        when(k8sConfig.getApiServer()).thenReturn("https://kubernetes.default.svc");
        when(k8sConfig.getNamespace()).thenReturn("merchant-acquiring");
        when(k8sConfig.getDeployment()).thenReturn("merchant-onboarding-service");
        tool = new InfraMonitorTool(config, restTemplate);
    }

    @Test
    void checkInfrastructure_returnsFormattedMetrics() throws Exception {
        JsonNode metricsResponse = mapper.readTree(
                "{\"items\":[{\"containers\":[{\"usage\":{\"cpu\":\"250m\",\"memory\":\"256Mi\"}}]}]}");
        JsonNode podsResponse = mapper.readTree(
                "{\"items\":[{\"spec\":{\"containers\":[{\"resources\":{\"limits\":{\"cpu\":\"500m\",\"memory\":\"512Mi\"}}}]}}]}");

        when(restTemplate.getForObject(
                anyString(), eq(JsonNode.class)))
                .thenReturn(metricsResponse)
                .thenReturn(podsResponse);

        String output = tool.checkInfrastructure();

        assertThat(output).contains("K8s Infrastructure Metrics");
        assertThat(output).contains("CPU Utilization: 50.0%");
        assertThat(output).contains("Memory Utilization: 50.0%");
    }

    @Test
    void collectMetrics_returnsInfraResult() throws Exception {
        JsonNode metricsResponse = mapper.readTree(
                "{\"items\":[{\"containers\":[{\"usage\":{\"cpu\":\"100m\",\"memory\":\"128Mi\"}}]}]}");
        JsonNode podsResponse = mapper.readTree(
                "{\"items\":[{\"spec\":{\"containers\":[{\"resources\":{\"limits\":{\"cpu\":\"1\",\"memory\":\"512Mi\"}}}]}}]}");

        when(restTemplate.getForObject(
                anyString(), eq(JsonNode.class)))
                .thenReturn(metricsResponse)
                .thenReturn(podsResponse);

        InfraResult result = tool.collectMetrics();

        assertThat(result.cpuUtilPercent()).isEqualTo(10.0);
        assertThat(result.memoryUtilPercent()).isEqualTo(25.0);
        assertThat(result.memoryUsedBytes()).isEqualTo(128L * 1024L * 1024L);
        assertThat(result.memoryLimitBytes()).isEqualTo(512L * 1024L * 1024L);
    }

    @Test
    void collectMetrics_onError_returnsZeroResult() {
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class)))
                .thenThrow(new RestClientException("Connection refused"));

        InfraResult result = tool.collectMetrics();

        assertThat(result.cpuUtilPercent()).isEqualTo(0.0);
        assertThat(result.memoryUtilPercent()).isEqualTo(0.0);
    }
}
