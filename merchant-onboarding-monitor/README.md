# Merchant Onboarding Monitor - AI Agent

AI-powered monitoring agent for merchant onboarding systems, built with **LangChain4j** and **Spring Boot**. The agent uses natural language to analyze logs, check Kafka consumer lag, inspect Kubernetes infrastructure, evaluate health thresholds, and generate Slack reports.

## Architecture

The agent exposes a single chat endpoint (`POST /api/agent/chat`) backed by an OpenAI-powered LangChain4j AI service. It has access to five tools:

| Tool | Description |
|------|-------------|
| **LogAnalysisTool** | Parses ELF access logs; computes TPS, error%, P50/P95/P99 latency, per-endpoint breakdown |
| **KafkaLagTool** | Checks Kafka consumer group lag via AdminClient |
| **InfraMonitorTool** | Collects K8s pod CPU/memory metrics via metrics.k8s.io REST API |
| **ReportTool** | Generates Slack-formatted reports and sends them via webhook |
| **ThresholdTool** | Reads threshold config and evaluates system health |

## Setup

### Prerequisites

- Java 17+
- Maven 3.8+
- OpenAI API key

### Configuration

Set the `OPENAI_API_KEY` environment variable:

```bash
export OPENAI_API_KEY=sk-...
```

Additional configuration is in `src/main/resources/application.yml`:

| Property | Description | Default |
|----------|-------------|---------|
| `agent.openai.api-key` | OpenAI API key | `${OPENAI_API_KEY:}` |
| `agent.openai.model-name` | Model to use | `gpt-4` |
| `agent.openai.temperature` | Response temperature | `0.3` |
| `monitor.elf.log-path` | Path to ELF access log | `/var/log/merchant-onboarding/access.log` |
| `monitor.kafka.bootstrap-servers` | Kafka brokers | `localhost:9092` |
| `monitor.kafka.consumer-group` | Kafka consumer group to monitor | `merchant-onboarding-consumer` |
| `monitor.kubernetes.api-server` | K8s API server URL | `https://kubernetes.default.svc` |
| `monitor.kubernetes.namespace` | K8s namespace | `merchant-acquiring` |
| `monitor.kubernetes.deployment` | Deployment name (label selector) | `merchant-onboarding-service` |
| `monitor.notification.slack-webhook-url` | Slack webhook URL | (placeholder) |
| `monitor.thresholds.*` | Warning/critical thresholds | See `application.yml` |

### Build & Run

```bash
cd merchant-onboarding-monitor
mvn clean compile
mvn spring-boot:run
```

## API

### POST /api/agent/chat

Send a natural language message to the monitoring agent.

**Request:**
```json
{
  "sessionId": "session-123",
  "message": "Analyze the last 6 hours of access logs"
}
```

**Response:**
```json
{
  "sessionId": "session-123",
  "response": "=== Log Analysis (last 6 hours) ===\nTotal Requests: 15234\nTPS: 0.70\n..."
}
```

**Example curl:**
```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "s1", "message": "Analyze the last 6 hours of access logs"}'
```

## Example Queries

1. **Log Analysis:**
   > "Analyze the last 6 hours of access logs and show me the top endpoints by error rate"

2. **Kafka Lag:**
   > "Check the current Kafka consumer lag for the onboarding topics"

3. **Infrastructure Check:**
   > "What are the current CPU and memory utilization for the K8s pods?"

4. **Report Generation:**
   > "Generate a full monitoring report and send it to Slack"

## Project Structure

```
merchant-onboarding-monitor/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/merchant/monitor/
    │   ├── MonitorApplication.java          # @SpringBootApplication
    │   ├── agent/
    │   │   ├── MerchantMonitorAgent.java    # LangChain4j AI service interface
    │   │   ├── AgentConfig.java             # Wires OpenAI model + tools + memory
    │   │   └── AgentController.java         # REST controller POST /api/agent/chat
    │   ├── config/
    │   │   └── MonitorConfig.java           # @ConfigurationProperties
    │   ├── tool/
    │   │   ├── LogAnalysisTool.java         # ELF log analysis @Tool
    │   │   ├── KafkaLagTool.java            # Kafka consumer lag @Tool
    │   │   ├── InfraMonitorTool.java        # K8s infra metrics @Tool
    │   │   ├── ReportTool.java              # Report generation @Tool
    │   │   └── ThresholdTool.java           # Threshold evaluation @Tool
    │   ├── parser/
    │   │   ├── ElfLogEntry.java             # Log entry record
    │   │   └── ElfLogParser.java            # Regex-based ELF log parser
    │   ├── metrics/
    │   │   ├── MetricsResult.java           # Log metrics record
    │   │   ├── LogMetricsCalculator.java    # TPS, error%, latency percentiles
    │   │   ├── KafkaLagResult.java          # Kafka lag record
    │   │   ├── KafkaLagCollector.java       # AdminClient-based lag collection
    │   │   └── InfraResult.java             # Infrastructure metrics record
    │   ├── report/
    │   │   ├── Report.java                  # Report record
    │   │   └── ReportRenderer.java          # Slack markdown renderer
    │   └── notification/
    │       └── SlackNotifier.java           # Slack webhook notifier
    └── resources/
        └── application.yml                  # Configuration
```
