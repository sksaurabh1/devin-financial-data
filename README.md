<img width="1920" height="1080" alt="Screenshot 2026-03-20 at 2 05 36 PM (2)" src="https://github.com/user-attachments/assets/d5be0099-6917-4429-94f8-f6dfd4c2d4de" />

Detailed Flow & Architecture — Component Level
Repository Structure
The devin-financial-data repository contains two independent systems:

Merchant Onboarding Monitor — a Spring Boot AI agent using LangChain4j

Fraud Risk Scorer — a standalone Python script

1. High-Level System Architecture















































2. Package & Class Diagram




























































































3. Request Flow — Chat Interaction (Sequence)



The AgentController receives a ChatRequest(sessionId, message) record and delegates to the LangChain4j-proxied MerchantMonitorAgent interface. AgentController.java:18-22 The agent is wired with all five tools and a 30-message chat memory window via AiServices.builder(). AgentConfig.java:45-49

4. Tool-Level Data Flows
4a. Log Analysis Flow











ElfLogParser uses a regex pattern to extract remoteHost, timestamp, method, uri, statusCode, bytes, and latencyMs from each log line, filtering by the [from, to] time window. ElfLogParser.java:24-26 LogMetricsCalculator computes TPS, error rate, success ratio, percentile latencies (p50/p95/p99), and per-endpoint breakdowns. LogMetricsCalculator.java:20-24

4b. Kafka Lag Flow














KafkaLagCollector creates an AdminClient, fetches committed offsets for the configured consumer group, then fetches latest offsets for partitions matching the configured topicPrefix. Lag = endOffset - committedOffset. KafkaLagCollector.java:30-73

4c. Infrastructure Monitor Flow














InfraMonitorTool makes two REST calls to the K8s API server: one to the metrics API for actual usage, and one to the core API for resource limits. It parses CPU values (nanocores/millicores/cores) and memory values (Ki/Mi/Gi/etc.). InfraMonitorTool.java:46-106

4d. Threshold Evaluation Flow













ThresholdTool.evaluateHealth() takes five live metric values and compares each against the configured warn/critical thresholds from MonitorConfig.Thresholds. ThresholdTool.java:36-41 The overall status is DEGRADED if any metric breaches its warning threshold. ThresholdTool.java:90-96

4e. Report Generation & Slack Dispatch Flow














ReportTool.buildReport() aggregates data from all three data sources (logs, Kafka, K8s) into a Report record. ReportTool.java:71-82 ReportRenderer produces a Slack-formatted markdown table with threshold-based status emojis. ReportRenderer.java:24-78 SlackNotifier sends the rendered markdown as a JSON payload to the configured webhook URL. SlackNotifier.java:26-36

5. Fraud Risk Scorer Flow (Python)

























The scorer uses a two-pass approach: Pass 1 builds frequency counters for nameDest and (nameOrig, step) pairs. fraud_risk_scorer.py:19-28 Pass 2 applies six additive scoring rules, capping at 100. fraud_risk_scorer.py:46-70

6. Configuration Model
MonitorConfig (bound to monitor.* properties) contains five nested config sections:

Section	Key Properties	Used By
Elf	logPath	LogAnalysisTool, ReportTool
Kafka	bootstrapServers, consumerGroup, topicPrefix	KafkaLagCollector
Kubernetes	apiServer, namespace, deployment	InfraMonitorTool
Notification	slackWebhookUrl	SlackNotifier
Thresholds	errorPercentWarn/Critical, p95LatencyWarnMs/CriticalMs, kafkaLagWarn/Critical, cpuUtilWarn, memoryUtilWarn	ThresholdTool, ReportRenderer
7. Spring Boot Wiring Summary
MonitorApplication bootstraps the app and enables MonitorConfig via @EnableConfigurationProperties. MonitorApplication.java:9-11 AgentConfig creates the ChatLanguageModel bean (OpenAI) and the MerchantMonitorAgent proxy via AiServices.builder(), injecting all five @Tool components and a 30-message sliding chat memory. AgentConfig.java:37-49 The MerchantMonitorAgent interface carries a @SystemMessage that instructs the LLM to act as a monitoring assistant. MerchantMonitorAgent.java:7-12
