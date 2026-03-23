package com.merchant.monitor.agent;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final MerchantMonitorAgent agent;

    public AgentController(MerchantMonitorAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = agent.chat(request.sessionId(), request.message());
        return new ChatResponse(request.sessionId(), response);
    }

    public record ChatRequest(String sessionId, String message) {}

    public record ChatResponse(String sessionId, String response) {}
}
