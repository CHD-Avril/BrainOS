package com.brainos.ai.embedding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "brainos.ai.embedding")
public record QwenEmbeddingProperties(
        @NotBlank String baseUrl,
        @NotBlank String model,
        @Min(1) @Max(4096) int dimensions,
        String apiKey) {}
