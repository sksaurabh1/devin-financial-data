<img width="1920" height="1080" alt="Screenshot 2026-03-20 at 2 05 36 PM (2)" src="https://github.com/user-attachments/assets/d5be0099-6917-4429-94f8-f6dfd4c2d4de" />

onboarding Agent flow : 

<img width="1470" height="956" alt="Screenshot 2026-03-20 at 2 34 17 PM" src="https://github.com/user-attachments/assets/a751915c-5d5b-41fd-8486-e66c04d7d7ef" />


Detailed Flow & Architecture — Component Level
Repository Structure
The devin-financial-data repository contains two independent systems:

Merchant Onboarding Monitor — a Spring Boot AI agent using LangChain4j

Deep Wiki: devin-financial-data — Branch merchant-onboarding-ai-agent
1. Repository Overview
The devin-financial-data repository on the merchant-onboarding-ai-agent branch provides a suite of tools for monitoring and analyzing merchant onboarding operations. It contains two independent functional components:

Component	Language	Location	Purpose
Merchant Onboarding Monitor (AI Agent)	Java 17 / Spring Boot 3.2	merchant-onboarding-monitor/	LangChain4j-powered conversational agent for infrastructure monitoring
Fraud Risk Scorer	Python 3 (stdlib only)	scripts/fraud_risk_scorer.py	Additive risk scoring on transaction data
2. Directory Structure

devin-financial-data/  
├── data/  
│   └── Example1.csv                          # Tab-delimited synthetic transaction data (100 rows)  
├── scripts/  
│   └── fraud_risk_scorer.py                  # Standalone fraud scoring script  
└── merchant-onboarding-monitor/  
    ├── pom.xml                               # Maven build (Spring Boot 3.2, LangChain4j 0.35.0)  
    ├── README.md  
    └── src/  
        ├── main/java/com/merchant/monitor/  
        │   ├── MonitorApplication.java       # @SpringBootApplication entry point  
        │   ├── agent/  
        │   │   ├── MerchantMonitorAgent.java # LangChain4j AI Service interface  
        │   │   ├── AgentConfig.java          # Wires OpenAI model + tools + memory  
        │   │   └── AgentController.java      # REST controller: POST /api/agent/chat  
        │   ├── config/  
        │   │   └── MonitorConfig.java        # @ConfigurationProperties (monitor.*)  
        │   ├── tool/  
        │   │   ├── LogAnalysisTool.java      # ELF log analysis @Tool  
        │   │   ├── KafkaLagTool.java         # Kafka consumer lag @Tool  
        │   │   ├── InfraMonitorTool.java     # K8s infra metrics @Tool  
        │   │   ├── ThresholdTool.java        # Health threshold evaluation @Tool  
        │   │   └── ReportTool.java           # Report generation + Slack dispatch @Tool  
        │   ├── parser/  
        │   │   ├── ElfLogEntry.java          # Log entry record  
        │   │   └── ElfLogParser.java         # Regex-based ELF log parser  
        │   ├── metrics/  
        │   │   ├── MetricsResult.java        # Log metrics record  
        │   │   ├── LogMetricsCalculator.java # TPS, error%, latency percentiles  
        │   │   ├── KafkaLagResult.java       # Kafka lag record  
        │   │   ├── KafkaLagCollector.java    # AdminClient-based lag collection  
        │   │   └── InfraResult.java          # Infrastructure metrics record  
        │   ├── report/  
        │   │   ├── Report.java               # Report record (aggregated data)  
        │   │   └── ReportRenderer.java       # Slack markdown renderer  
        │   └── notification/  
        │       └── SlackNotifier.java        # Slack webhook HTTP client  
        └── test/java/com/merchant/monitor/tool/  
            ├── LogAnalysisToolTest.java  
            ├── KafkaLagToolTest.java  
            ├── InfraMonitorToolTest.java  
            ├── ThresholdToolTest.java  
            └── ReportToolTest.java
README.md:100-137

3. AI Agent Architecture
3.1 High-Level Flow
The AI Agent uses LangChain4j to translate natural language queries into tool invocations against infrastructure systems (logs, Kafka, Kubernetes) and return synthesized responses.

<img width="1470" height="956" alt="Screenshot 2026-03-20 at 2 26 33 PM" src="https://github.com/user-attachments/assets/b2fdd81b-f11a-41c5-af0c-3404a99aa74a" />

High level architecture 

<img width="1920" height="1080" alt="Screenshot 2026-03-20 at 2 16 50 PM (2)" src="https://github.com/user-attachments/assets/4c8c96ff-3e1d-4df8-a164-662519e0305f" />
















3.2 Entry Point: MonitorApplication
The Spring Boot application entry point enables MonitorConfig via @EnableConfigurationProperties:


@SpringBootApplication  
@EnableConfigurationProperties(MonitorConfig.class)  
public class MonitorApplication {  
    public static void main(String[] args) {  
        SpringApplication.run(MonitorApplication.class, args);  
    }  
}
MonitorApplication.java:9-15

3.3 REST API: AgentController
Single endpoint POST /api/agent/chat accepting ChatRequest(sessionId, message) and returning ChatResponse(sessionId, response):


@PostMapping("/chat")  
public ChatResponse chat(@RequestBody ChatRequest request) {  
    String response = agent.chat(request.sessionId(), request.message());  
    return new ChatResponse(request.sessionId(), response);  
}
AgentController.java:18-22

3.4 AI Service Interface: MerchantMonitorAgent
A LangChain4j AiServices interface with a @SystemMessage defining the agent persona and @MemoryId for session isolation:


public interface MerchantMonitorAgent {  
    @SystemMessage("You are a monitoring assistant for Merchant Onboarding. " +  
            "Use tools to analyze ELF access logs, check Kafka consumer lag, inspect " +  
            "K8s pod metrics, evaluate thresholds, and generate Slack reports.")  
    String chat(@MemoryId String sessionId, @UserMessage String message);  
}
MerchantMonitorAgent.java:7-13

3.5 Agent Wiring: AgentConfig
The AgentConfig class constructs the OpenAiChatModel bean and wires the agent with 5 tools and a MessageWindowChatMemory (30 messages per session):


@Bean  
public MerchantMonitorAgent merchantMonitorAgent(...) {  
    return AiServices.builder(MerchantMonitorAgent.class)  
            .chatLanguageModel(chatModel)  
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(30))  
            .tools(logAnalysisTool, kafkaLagTool, infraMonitorTool, reportTool, thresholdTool)  
            .build();  
}
LLM parameters are injected from application.yml via @Value:

agent.openai.api-key (default: ${OPENAI_API_KEY:})
agent.openai.model-name (default: gpt-4)
agent.openai.temperature (default: 0.3) AgentConfig.java:37-50
4. Configuration: MonitorConfig
All monitoring settings are bound via @ConfigurationProperties(prefix = "monitor") into nested POJOs: MonitorConfig.java:5-12

Section	Key Properties	Defaults
monitor.elf	log-path	/var/log/merchant-onboarding/access.log
monitor.kafka	bootstrap-servers, consumer-group, topic-prefix	localhost:9092, merchant-onboarding-consumer, merchant-onboarding
monitor.kubernetes	api-server, namespace, deployment	https://kubernetes.default.svc, merchant-acquiring, merchant-onboarding-service
monitor.notification	slack-webhook-url	(placeholder)
monitor.thresholds	error-percent-warn/critical, p95-latency-warn-ms/critical-ms, kafka-lag-warn/critical, cpu-util-warn, memory-util-warn	2%/5%, 500ms/1000ms, 1000/5000, 80%, 75%
5. Monitoring Tools (@Tool Components)
5.1 LogAnalysisTool
Parses ELF access logs and computes performance metrics. Exposes two @Tool methods:

analyzeRecentLogs(hoursBack) — Broad system overview: TPS, error rate, P50/P95/P99 latency, top endpoints by traffic and error rate.
getEndpointMetrics(path, hours) — Filtered analysis for a specific URI.
Internally delegates to ElfLogParser (regex-based line parser) and LogMetricsCalculator (statistical computation). LogAnalysisTool.java:31-32

ElfLogParser uses the regex ^(\S+) \S+ \S+ \[([^\]]+)\] "(\S+) (\S+) \S+" (\d{3}) (\d+|-) (\d+)$ and parses timestamps with dd/MMM/yyyy:HH:mm:ss Z. Each line maps to an ElfLogEntry record. ElfLogParser.java:24-29

LogMetricsCalculator.compute() calculates TPS (totalRequests / windowSeconds), error rate (status >= 400), success ratio (200 <= status < 300), and latency percentiles by sorting and index selection. LogMetricsCalculator.java:20-27

MetricsResult record fields: totalRequests, tps, errorPercent, successRatio, p50Ms, p95Ms, p99Ms, avgLatencyMs, requestsByEndpoint, errorRateByEndpoint, requestsByHour. MetricsResult.java:5-17

5.2 KafkaLagTool
Single @Tool method checkKafkaLag() that delegates to KafkaLagCollector. Returns total lag and per-partition breakdown. KafkaLagTool.java:20-21

KafkaLagCollector creates an AdminClient, fetches committed offsets via listConsumerGroupOffsets(), fetches end offsets via listOffsets(OffsetSpec.latest()), and computes lag as Math.max(0, endOffset - committedOffset). Topics are filtered by topicPrefix. On error, returns totalLag = -1. The AdminClient is closed in a finally block. KafkaLagCollector.java:30-82

5.3 InfraMonitorTool
@Tool method checkInfrastructure() queries Kubernetes for CPU/memory utilization. Internally calls collectMetrics() which:

Queries metrics.k8s.io/v1beta1 for pod usage (nanocores + bytes)
Queries v1/pods for resource limits
Calculates utilization = usage / limit * 100
Includes robust parsers for K8s resource formats: parseCpuNanocores() handles n, m, and bare core values; parseMemoryBytes() handles Ki/Mi/Gi/Ti (binary) and K/M/G/T (decimal) suffixes.

On any error, returns zeroed InfraResult. InfraMonitorTool.java:32-33 InfraMonitorTool.java:108-138

5.4 ThresholdTool
Two @Tool methods:

getThresholds() — Returns formatted string of all configured threshold values.
evaluateHealth(errorPct, p95, kafkaLag, cpu, mem) — Compares each metric against warn/critical thresholds and returns per-metric status (OK/WARNING/CRITICAL/UNKNOWN) plus overall HEALTHY or DEGRADED.
Key logic: negative Kafka lag (< 0) is treated as UNKNOWN and triggers DEGRADED. Overall status is DEGRADED if any metric exceeds its warning threshold. ThresholdTool.java:35-41 ThresholdTool.java:90-96

5.5 ReportTool
Orchestrates all other data sources into a unified report:

generateReport() — Builds a Report record from the last 6 hours of data, renders to Slack markdown via ReportRenderer.
generateAndSendReport() — Same as above, plus dispatches via SlackNotifier.
The buildReport() method aggregates ElfLogParser + LogMetricsCalculator + KafkaLagCollector + InfraMonitorTool.collectMetrics() into a Report(periodStart, periodEnd, logMetrics, kafkaLag, infra) record. ReportTool.java:71-82

ReportRenderer produces Slack-formatted markdown with a summary table, status emojis (:white_check_mark:, :warning:, :red_circle:, :question:), and Top 5 endpoints by traffic and error rate. ReportRenderer.java:24-32

SlackNotifier uses Java's HttpClient to POST a JSON payload {"text": "..."} to the configured webhook URL, with manual JSON escaping. SlackNotifier.java:26-34

6. Data Records
Record	Package	Fields
ElfLogEntry	parser	remoteHost, timestamp, method, uri, statusCode, bytes, latencyMs
MetricsResult	metrics	totalRequests, tps, errorPercent, successRatio, p50Ms, p95Ms, p99Ms, avgLatencyMs, requestsByEndpoint, errorRateByEndpoint, requestsByHour
KafkaLagResult	metrics	totalLag, lagByPartition
InfraResult	metrics	cpuUtilPercent, memoryUtilPercent, memoryUsedBytes, memoryLimitBytes
Report	report	periodStart, periodEnd, logMetrics, kafkaLag, infra
7. Dependencies (Maven)
Dependency	Version	Purpose
spring-boot-starter-web	3.2.0 (parent)	REST API
kafka-clients	3.6.1	Kafka AdminClient for lag collection
jackson-databind	(managed)	JSON parsing for K8s API responses
langchain4j	0.35.0	Core AI orchestration
langchain4j-open-ai	0.35.0	OpenAI model integration
langchain4j-spring-boot-starter	0.35.0	Spring Boot auto-configuration
spring-boot-starter-test	(managed)	JUnit 5 + Mockito

