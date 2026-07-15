package com.brainos.document.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.ai.embedding.EmbeddingPort;
import com.brainos.document.chunking.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
class ChromaVectorIndexIT {

    @Container
    static final GenericContainer<?> CHROMA = new GenericContainer<>(
                    DockerImageName.parse("ghcr.io/chroma-core/chroma:1.0.0"))
            .withExposedPorts(8000)
            .withReuse(false);

    private static final AtomicLong IDS = new AtomicLong(3_000_000_000L);

    private final List<Long> documentsToDelete = new ArrayList<>();

    @AfterEach
    void cleanDocuments() {
        if (CHROMA.isRunning()) {
            SpringAiVectorIndex index = index();
            documentsToDelete.forEach(index::deleteDocument);
        }
    }

    @Test
    void addsAndSearchesWithStableIdMetadataAndScore() {
        long knowledgeBaseId = nextId();
        long documentId = nextId();
        replace(documentId, List.of(chunk(knowledgeBaseId, documentId, 0, "leave policy", 5)));

        List<RetrievedChunk> results = index().search(
                knowledgeBaseId, new float[] {1f, 0f, 0f}, 3, 0.8d);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(documentId + ":0");
            assertThat(result.text()).isEqualTo("leave policy");
            assertThat(result.knowledgeBaseId()).isEqualTo(knowledgeBaseId);
            assertThat(result.documentId()).isEqualTo(documentId);
            assertThat(result.fileName()).isEqualTo("handbook.pdf");
            assertThat(result.pageNumber()).isEqualTo(5);
            assertThat(result.chunkIndex()).isZero();
            assertThat(result.score()).isBetween(0.99d, 1.0d);
        });
    }

    @Test
    void serverSideKnowledgeBaseFilterNeverLeaksSimilarText() {
        long firstBase = nextId();
        long secondBase = nextId();
        long firstDocument = nextId();
        long secondDocument = nextId();
        replace(firstDocument, List.of(chunk(firstBase, firstDocument, 0, "same leave policy", 1)));
        replace(secondDocument, List.of(chunk(secondBase, secondDocument, 0, "same leave policy", 2)));

        List<RetrievedChunk> firstResults =
                index().search(firstBase, new float[] {1f, 0f, 0f}, 10, 0d);
        List<RetrievedChunk> secondResults =
                index().search(secondBase, new float[] {1f, 0f, 0f}, 10, 0d);

        assertThat(firstResults).extracting(RetrievedChunk::knowledgeBaseId).containsOnly(firstBase);
        assertThat(firstResults).extracting(RetrievedChunk::documentId).doesNotContain(secondDocument);
        assertThat(secondResults).extracting(RetrievedChunk::knowledgeBaseId).containsOnly(secondBase);
        assertThat(secondResults).extracting(RetrievedChunk::documentId).doesNotContain(firstDocument);
    }

    @Test
    void honorsTopKAndSimilarityThreshold() {
        long knowledgeBaseId = nextId();
        long exact = nextId();
        long near = nextId();
        replace(exact, List.of(chunk(knowledgeBaseId, exact, 0, "leave exact", null)));
        replace(near, List.of(chunk(knowledgeBaseId, near, 0, "near leave", null)));

        assertThat(index().search(knowledgeBaseId, new float[] {1f, 0f, 0f}, 1, 0d))
                .hasSize(1);
        assertThat(index().search(knowledgeBaseId, new float[] {1f, 0f, 0f}, 10, 0.99d))
                .extracting(RetrievedChunk::documentId)
                .containsExactly(exact);
    }

    @Test
    void replaceRemovesOldChunksAndDeleteRemovesDocument() {
        long knowledgeBaseId = nextId();
        long documentId = nextId();
        replace(documentId, List.of(
                chunk(knowledgeBaseId, documentId, 0, "leave old", 1),
                chunk(knowledgeBaseId, documentId, 1, "leave obsolete", 2)));

        replace(documentId, List.of(chunk(knowledgeBaseId, documentId, 0, "finance new", 7)));

        assertThat(index().search(knowledgeBaseId, new float[] {1f, 0f, 0f}, 10, 0.8d))
                .isEmpty();
        assertThat(index().search(knowledgeBaseId, new float[] {0f, 1f, 0f}, 10, 0.8d))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.id()).isEqualTo(documentId + ":0");
                    assertThat(result.text()).isEqualTo("finance new");
                    assertThat(result.pageNumber()).isEqualTo(7);
                });

        index().deleteDocument(documentId);
        assertThat(index().search(knowledgeBaseId, new float[] {0f, 1f, 0f}, 10, 0d))
                .isEmpty();
    }

    private void replace(long documentId, List<DocumentChunk> chunks) {
        documentsToDelete.add(documentId);
        index().replaceDocument(documentId, chunks);
    }

    private static long nextId() {
        return IDS.incrementAndGet();
    }

    private static DocumentChunk chunk(
            long knowledgeBaseId, long documentId, int index, String text, Integer pageNumber) {
        return new DocumentChunk(
                documentId + ":" + index,
                text,
                knowledgeBaseId,
                documentId,
                "handbook.pdf",
                pageNumber,
                index);
    }

    private static SpringAiVectorIndex index() {
        return new SpringAiVectorIndex(
                "http://" + CHROMA.getHost() + ":" + CHROMA.getMappedPort(8000),
                new DeterministicEmbedding());
    }

    private static final class DeterministicEmbedding implements EmbeddingPort {
        @Override
        public float[] embed(String text) {
            return vector(text);
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return texts.stream().map(DeterministicEmbedding::vector).toList();
        }

        private static float[] vector(String text) {
            if (text.startsWith("finance")) {
                return new float[] {0f, 1f, 0f};
            }
            if (text.startsWith("near")) {
                return new float[] {8f, 2f, 0f};
            }
            return new float[] {10f, 0f, 0f};
        }
    }
}
