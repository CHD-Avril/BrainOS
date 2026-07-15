package com.brainos.ai.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QwenEmbeddingProperties.class)
class AiEmbeddingConfiguration {

    @Bean
    EmbeddingPort qwenEmbeddingPort(QwenEmbeddingProperties properties) {
        return new QwenEmbeddingAdapter(properties);
    }
}
