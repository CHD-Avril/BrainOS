package com.brainos.document.indexing;

import com.brainos.ai.embedding.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class VectorIndexConfiguration {

    @Bean
    VectorIndexPort vectorIndexPort(
            @Value("${brainos.chroma.base-url}") String baseUrl,
            EmbeddingPort embeddings) {
        return new SpringAiVectorIndex(baseUrl, embeddings);
    }
}
