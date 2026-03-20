# Merchant Onboarding Performance Monitor

Java agent that runs every 6 hours, parses ELF access logs, collects Kafka consumer lag and Kubernetes pod metrics, and generates a Slack report with threshold-based alerting.

## Prerequisites

- Java 17+
- Maven 3.8+
- Access to Kafka cluster (for consumer lag metrics)
- Access to Kubernetes cluster with metrics-server (for pod CPU/memory metrics)
- Slack incoming webhook URL (for report delivery)

## Configuration

All configuration is managed via `src/main/resources/application.yml` under the `monitor` prefix:

| Property | Description | Default |
|----------|-------------|---------|
| `monitor.elf.log-path` | Path to the ELF access log file | `/var/log/merchant-onboarding/access.log` |
| `monitor.kafka.bootstrap-servers` | Kafka bootstrap servers | `localhost:9092` |
| `monitor.kafka.consumer-group` | Kafka consumer group to monitor | `merchant-onboarding-consumer` |
| `monitor.kafka.topic-prefix` | Topic prefix filter for lag calculation | `merchant-onboarding` |
| `monitor.kubernetes.namespace` | Kubernetes namespace for pod metrics | `merchant-acquiring` |
| `monitor.kubernetes.deployment` | Deployment name (used as `app=` label selector) | `merchant-onboarding-service` |
| `monitor.notification.slack-webhook-url` | Slack incoming webhook URL | `https://hooks.slack.com/services/XXX/YYY/ZZZ` |
| `monitor.schedule.cron` | Cron expression for report generation | `0 0 */6 * * *` (every 6 hours) |
| `monitor.thresholds.error-percent-warn` | Error% warning threshold | `2.0` |
| `monitor.thresholds.error-percent-critical` | Error% critical threshold | `5.0` |
| `monitor.thresholds.p95-latency-warn-ms` | P95 latency warning threshold (ms) | `500` |
| `monitor.thresholds.p95-latency-critical-ms` | P95 latency critical threshold (ms) | `1000` |
| `monitor.thresholds.kafka-lag-warn` | Kafka lag warning threshold | `1000` |
| `monitor.thresholds.kafka-lag-critical` | Kafka lag critical threshold | `5000` |
| `monitor.thresholds.memory-util-warn` | Memory utilization warning threshold (%) | `75.0` |
| `monitor.thresholds.cpu-util-warn` | CPU utilization warning threshold (%) | `80.0` |

## Build

```bash
cd merchant-onboarding-monitor
mvn clean package -DskipTests
```

## Run

```bash
java -jar target/merchant-onboarding-monitor-1.0.0-SNAPSHOT.jar
```

Override configuration at runtime with environment variables or system properties:

```bash
java -jar target/merchant-onboarding-monitor-1.0.0-SNAPSHOT.jar \
  --monitor.notification.slack-webhook-url=https://hooks.slack.com/services/REAL/URL/HERE \
  --monitor.elf.log-path=/path/to/access.log
```

## Expected ELF Log Format

The parser expects Extended Log Format (ELF) lines matching this pattern:

```
<remote_host> <ident> <user> [<timestamp>] "<method> <uri> <protocol>" <status> <bytes> <latency_ms>
```

Example line:

```
192.168.1.1 - - [20/Mar/2026:10:15:30 +0000] "POST /api/v1/merchants/onboard HTTP/1.1" 200 1234 45
```

| Field | Description |
|-------|-------------|
| `remote_host` | Client IP address (`192.168.1.1`) |
| `timestamp` | Request timestamp in `dd/MMM/yyyy:HH:mm:ss Z` format |
| `method` | HTTP method (`POST`) |
| `uri` | Request URI (`/api/v1/merchants/onboard`) |
| `status` | HTTP status code (`200`) |
| `bytes` | Response size in bytes (`1234`), or `-` if unknown |
| `latency_ms` | Request latency in milliseconds (`45`) |

## Sample Report Output

The monitor generates Slack markdown reports like the following:

```
*:bar_chart: Merchant Onboarding Monitor Report*
_Period: 2026-03-20 04:00:00 — 2026-03-20 10:00:00 UTC_

*Summary*
| Metric | Value | Status |
|--------|-------|--------|
| Total Requests | 48210 | :white_check_mark: |
| TPS | 2.23 | :white_check_mark: |
| Error% | 1.45% | :white_check_mark: |
| Success Ratio | 0.9812 | :white_check_mark: |
| P95 Latency | 420ms | :white_check_mark: |
| P99 Latency | 890ms | :white_check_mark: |
| Kafka Lag | 312 | :white_check_mark: |
| Memory% | 62.3% | :white_check_mark: |
| CPU% | 45.7% | :white_check_mark: |

*Top 5 Endpoints by Traffic*
• `/api/v1/merchants/onboard` — 12450 requests
• `/api/v1/merchants/status` — 9820 requests
• `/api/v1/merchants/documents` — 8340 requests
• `/api/v1/merchants/verify` — 6210 requests
• `/api/v1/merchants/activate` — 4890 requests

*Top 5 Endpoints by Error Rate*
• `/api/v1/merchants/verify` — 4.21%
• `/api/v1/merchants/documents` — 2.87%
• `/api/v1/merchants/onboard` — 1.12%
• `/api/v1/merchants/activate` — 0.85%
• `/api/v1/merchants/status` — 0.34%
```

Status emojis are determined by threshold configuration:
- :white_check_mark: — within normal range
- :warning: — exceeds warning threshold
- :red_circle: — exceeds critical threshold
- :question: — data unavailable

## Project Structure

```
merchant-onboarding-monitor/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/merchant/monitor/
    │   ├── MonitorApplication.java          # @SpringBootApplication @EnableScheduling
    │   ├── config/
    │   │   └── MonitorConfig.java           # @ConfigurationProperties with nested classes
    │   ├── parser/
    │   │   ├── ElfLogEntry.java             # Log entry record
    │   │   └── ElfLogParser.java            # Regex-based ELF log parser
    │   ├── metrics/
    │   │   ├── MetricsResult.java           # Log metrics record
    │   │   ├── LogMetricsCalculator.java    # TPS, error%, P50/P95/P99, per-endpoint
    │   │   ├── KafkaLagResult.java          # Kafka lag record
    │   │   ├── KafkaLagCollector.java       # AdminClient-based lag collection
    │   │   ├── InfraResult.java             # Infrastructure metrics record
    │   │   └── InfraCollector.java          # K8s metrics.k8s.io pod metrics
    │   ├── report/
    │   │   ├── Report.java                  # Report record
    │   │   └── ReportRenderer.java          # Slack markdown renderer
    │   ├── notification/
    │   │   └── SlackNotifier.java           # Slack webhook notifier
    │   └── scheduler/
    │       └── ReportScheduler.java         # Cron-scheduled report orchestrator
    └── resources/
        └── application.yml                  # Configuration
```
