package com.brainos.ai.embedding;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public final class QwenEmbeddingAdapter implements EmbeddingPort {

    private final QwenEmbeddingProperties properties;
    private volatile EmbeddingModel model;

    public QwenEmbeddingAdapter(QwenEmbeddingProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new EmbeddingNotConfiguredException();
        }
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("embedding input must contain non-blank text");
        }
        return model().embed(List.copyOf(texts));
    }

    private EmbeddingModel model() {
        EmbeddingModel current = model;
        if (current == null) {
            synchronized (this) {
                current = model;
                if (current == null) {
                    OpenAiApi api = OpenAiApi.builder()
                            .baseUrl(stripTrailingSlash(properties.baseUrl()))
                            .apiKey(properties.apiKey())
                            .embeddingsPath("/embeddings")
                            .build();
                    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                            .model(properties.model())
                            .dimensions(properties.dimensions())
                            .build();
                    current = new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
                    model = current;
                }
            }
        }
        return current;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
