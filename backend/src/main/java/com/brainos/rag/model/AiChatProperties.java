package com.brainos.rag.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("brainos.ai.chat")
public record AiChatProperties(Provider qwen, Provider deepseek, Provider chatgpt) {

    public record Provider(String baseUrl, String model, String apiKey) {}
}
