package com.brainos.document.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.ai.embedding.EmbeddingPort;
import com.brainos.document.chunking.DocumentChunk;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
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

    @Test
    void sendsCloudTokenAndUsesConfiguredTenantAndDatabase() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"collection-id","name":"brainos_documents",\
                            "metadata":{"hnsw:space":"cosine"}}
                            """));
            server.enqueue(new MockResponse().setResponseCode(200));
            server.start();

            SpringAiVectorIndex cloudIndex = new SpringAiVectorIndex(
                    server.url("/").toString(),
                    "cloud-api-key",
                    "tenant-123",
                    "brainos-cloud",
                    constantEmbedding());

            cloudIndex.deleteDocument(7L);

            RecordedRequest collectionRequest = server.takeRequest(2, TimeUnit.SECONDS);
            RecordedRequest deleteRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(collectionRequest).isNotNull();
            assertThat(collectionRequest.getPath())
                    .isEqualTo("/api/v2/tenants/tenant-123/databases/brainos-cloud/collections/brainos_documents");
            assertThat(collectionRequest.getHeader("x-chroma-token"))
                    .isEqualTo("cloud-api-key");
            assertThat(deleteRequest).isNotNull();
            assertThat(deleteRequest.getPath())
                    .isEqualTo("/api/v2/tenants/tenant-123/databases/brainos-cloud/collections/collection-id/delete");
            assertThat(deleteRequest.getHeader("x-chroma-token"))
                    .isEqualTo("cloud-api-key");
        }
    }

    @Test
    void retriesTransientConnectionFailureWhenDeletingDocumentVectors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"collection-id","name":"brainos_documents",\
                            "metadata":{"hnsw:space":"cosine"}}
                            """));
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.enqueue(new MockResponse().setResponseCode(200));
            server.start();
            SpringAiVectorIndex cloudIndex = new SpringAiVectorIndex(
                    server.url("/").toString(),
                    "cloud-api-key",
                    "tenant-123",
                    "brainos-cloud",
                    constantEmbedding());

            cloudIndex.deleteDocument(7L);

            assertThat(server.getRequestCount()).isEqualTo(3);
        }
    }

    @Test
    void rejectsBlankCloudRoutingValues() {
        assertThatThrownBy(() -> new SpringAiVectorIndex(
                        "https://api.trychroma.com", "key", " ", "database", constantEmbedding()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
        assertThatThrownBy(() -> new SpringAiVectorIndex(
                        "https://api.trychroma.com", "key", "tenant", " ", constantEmbedding()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database");
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
