package com.merchant.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public class MonitorConfig {

    private Elf elf = new Elf();
    private Kafka kafka = new Kafka();
    private Kubernetes kubernetes = new Kubernetes();
    private Notification notification = new Notification();
    private Schedule schedule = new Schedule();
    private Thresholds thresholds = new Thresholds();

    public Elf getElf() {
        return elf;
    }

    public void setElf(Elf elf) {
        this.elf = elf;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Kubernetes getKubernetes() {
        return kubernetes;
    }

    public void setKubernetes(Kubernetes kubernetes) {
        this.kubernetes = kubernetes;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public static class Elf {
        private String logPath;

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }
    }

    public static class Kafka {
        private String bootstrapServers;
        private String consumerGroup;
        private String topicPrefix;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getTopicPrefix() {
            return topicPrefix;
        }

        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }
    }

    public static class Kubernetes {
        private String namespace;
        private String deployment;

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getDeployment() {
            return deployment;
        }

        public void setDeployment(String deployment) {
            this.deployment = deployment;
        }
    }

    public static class Notification {
        private String slackWebhookUrl;

        public String getSlackWebhookUrl() {
            return slackWebhookUrl;
        }

        public void setSlackWebhookUrl(String slackWebhookUrl) {
            this.slackWebhookUrl = slackWebhookUrl;
        }
    }

    public static class Schedule {
        private String cron;

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Thresholds {
        private double errorPercentWarn;
        private double errorPercentCritical;
        private long p95LatencyWarnMs;
        private long p95LatencyCriticalMs;
        private long kafkaLagWarn;
        private long kafkaLagCritical;
        private double memoryUtilWarn;
        private double cpuUtilWarn;

        public double getErrorPercentWarn() {
            return errorPercentWarn;
        }

        public void setErrorPercentWarn(double errorPercentWarn) {
            this.errorPercentWarn = errorPercentWarn;
        }

        public double getErrorPercentCritical() {
            return errorPercentCritical;
        }

        public void setErrorPercentCritical(double errorPercentCritical) {
            this.errorPercentCritical = errorPercentCritical;
        }

        public long getP95LatencyWarnMs() {
            return p95LatencyWarnMs;
        }

        public void setP95LatencyWarnMs(long p95LatencyWarnMs) {
            this.p95LatencyWarnMs = p95LatencyWarnMs;
        }

        public long getP95LatencyCriticalMs() {
            return p95LatencyCriticalMs;
        }

        public void setP95LatencyCriticalMs(long p95LatencyCriticalMs) {
            this.p95LatencyCriticalMs = p95LatencyCriticalMs;
        }

        public long getKafkaLagWarn() {
            return kafkaLagWarn;
        }

        public void setKafkaLagWarn(long kafkaLagWarn) {
            this.kafkaLagWarn = kafkaLagWarn;
        }

        public long getKafkaLagCritical() {
            return kafkaLagCritical;
        }

        public void setKafkaLagCritical(long kafkaLagCritical) {
            this.kafkaLagCritical = kafkaLagCritical;
        }

        public double getMemoryUtilWarn() {
            return memoryUtilWarn;
        }

        public void setMemoryUtilWarn(double memoryUtilWarn) {
            this.memoryUtilWarn = memoryUtilWarn;
        }

        public double getCpuUtilWarn() {
            return cpuUtilWarn;
        }

        public void setCpuUtilWarn(double cpuUtilWarn) {
            this.cpuUtilWarn = cpuUtilWarn;
        }
    }
}
