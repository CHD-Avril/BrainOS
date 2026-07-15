package com.brainos.ai.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.openai.api.OpenAiApi;

public final class QwenEmbeddingAdapter implements EmbeddingPort {

    private static final int MAX_BATCH_SIZE = 10;

    private final QwenEmbeddingProperties properties;
    private volatile OpenAiApi api;

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
        List<String> input = List.copyOf(texts);
        List<float[]> vectors = new ArrayList<>(input.size());
        for (int start = 0; start < input.size(); start += MAX_BATCH_SIZE) {
            List<String> batch = input.subList(start, Math.min(start + MAX_BATCH_SIZE, input.size()));
            OpenAiApi.EmbeddingList<OpenAiApi.Embedding> response = api()
                    .embeddings(new OpenAiApi.EmbeddingRequest<>(
                            batch, properties.model(), "float", properties.dimensions(), null))
                    .getBody();
            vectors.addAll(orderedVectors(response, batch.size()));
        }
        return List.copyOf(vectors);
    }

    private OpenAiApi api() {
        OpenAiApi current = api;
        if (current == null) {
            synchronized (this) {
                current = api;
                if (current == null) {
                    current = OpenAiApi.builder()
                            .baseUrl(stripTrailingSlash(properties.baseUrl()))
                            .apiKey(properties.apiKey())
                            .embeddingsPath("/embeddings")
                            .build();
                    api = current;
                }
            }
        }
        return current;
    }

    private static List<float[]> orderedVectors(
            OpenAiApi.EmbeddingList<OpenAiApi.Embedding> response, int expectedSize) {
        if (response == null || response.data() == null || response.data().size() != expectedSize) {
            throw new IllegalStateException("embedding response count mismatch");
        }
        List<float[]> ordered = new ArrayList<>(expectedSize);
        for (int index = 0; index < expectedSize; index++) {
            ordered.add(null);
        }
        int dimensions = 0;
        for (OpenAiApi.Embedding item : response.data()) {
            if (item == null
                    || item.index() == null
                    || item.index() < 0
                    || item.index() >= expectedSize
                    || ordered.get(item.index()) != null) {
                throw new IllegalStateException("embedding response index mismatch");
            }
            float[] vector = item.embedding();
            if (vector == null || vector.length == 0) {
                throw new IllegalStateException("embedding response is empty");
            }
            if (dimensions == 0) {
                dimensions = vector.length;
            } else if (vector.length != dimensions) {
                throw new IllegalStateException("embedding response dimensions mismatch");
            }
            for (float coordinate : vector) {
                if (!Float.isFinite(coordinate)) {
                    throw new IllegalStateException("embedding response contains a non-finite value");
                }
            }
            ordered.set(item.index(), vector.clone());
        }
        if (ordered.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("embedding response index mismatch");
        }
        return List.copyOf(ordered);
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
