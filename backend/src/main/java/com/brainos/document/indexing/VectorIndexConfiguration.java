package com.brainos.document.indexing;

import com.brainos.ai.embedding.EmbeddingPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChromaConnectionProperties.class)
class VectorIndexConfiguration {

    @Bean
    VectorIndexPort vectorIndexPort(
            ChromaConnectionProperties properties,
            EmbeddingPort embeddings) {
        return new SpringAiVectorIndex(
                properties.baseUrl(),
                properties.apiKey(),
                properties.tenant(),
                properties.database(),
                embeddings);
    }
}
