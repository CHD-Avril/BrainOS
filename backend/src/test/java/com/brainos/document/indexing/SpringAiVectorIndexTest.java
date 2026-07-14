package com.brainos.document.indexing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.ai.embedding.EmbeddingPort;
import com.brainos.document.chunking.DocumentChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiVectorIndexTest {

    private final SpringAiVectorIndex index =
            new SpringAiVectorIndex("http://127.0.0.1:1", constantEmbedding());

    @Test
    void validatesSearchBoundariesBeforeCallingChroma() {
        assertThatThrownBy(() -> index.search(1L, null, 3, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[0], 3, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[] {Float.NaN}, 3, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[] {1f}, 0, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[] {1f}, 101, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[] {1f}, 3, -0.01d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.search(1L, new float[] {1f}, 3, 1.01d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedOrDuplicateChunksBeforeCallingChroma() {
        DocumentChunk mismatched = new DocumentChunk("3:0", "text", 1L, 3L, "a.txt", null, 0);
        DocumentChunk duplicate = new DocumentChunk("2:0", "text", 1L, 2L, "a.txt", null, 0);

        assertThatThrownBy(() -> index.replaceDocument(2L, List.of(mismatched)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.replaceDocument(2L, List.of(duplicate, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EmbeddingPort constantEmbedding() {
        return new EmbeddingPort() {
            @Override
            public float[] embed(String text) {
                return new float[] {1f};
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                return texts.stream().map(ignored -> new float[] {1f}).toList();
            }
        };
    }
}
