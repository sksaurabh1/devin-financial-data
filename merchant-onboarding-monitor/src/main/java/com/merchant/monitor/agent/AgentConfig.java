package com.merchant.monitor.agent;

import com.merchant.monitor.tool.InfraMonitorTool;
import com.merchant.monitor.tool.KafkaLagTool;
import com.merchant.monitor.tool.LogAnalysisTool;
import com.merchant.monitor.tool.ReportTool;
import com.merchant.monitor.tool.ThresholdTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Value("${agent.openai.api-key}")
    private String apiKey;

    @Value("${agent.openai.model-name}")
    private String modelName;

    @Value("${agent.openai.temperature}")
    private double temperature;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean
    public MerchantMonitorAgent merchantMonitorAgent(
            ChatLanguageModel chatModel,
            LogAnalysisTool logAnalysisTool,
            KafkaLagTool kafkaLagTool,
            InfraMonitorTool infraMonitorTool,
            ReportTool reportTool,
            ThresholdTool thresholdTool) {
        return AiServices.builder(MerchantMonitorAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(30))
                .tools(logAnalysisTool, kafkaLagTool, infraMonitorTool, reportTool, thresholdTool)
                .build();
    }
}
