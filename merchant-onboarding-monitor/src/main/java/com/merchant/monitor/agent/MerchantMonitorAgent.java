package com.merchant.monitor.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface MerchantMonitorAgent {

    @SystemMessage("You are a monitoring assistant for Merchant Onboarding. " +
            "Use tools to analyze ELF access logs, check Kafka consumer lag, inspect " +
            "K8s pod metrics, evaluate thresholds, and generate Slack reports.")
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
