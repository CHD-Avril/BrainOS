package com.brainos.document.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.document.chunking.DocumentChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorIndexContractTest {

    @Test
    void retrievedChunkKeepsStableDocumentMetadata() {
        RetrievedChunk chunk = new RetrievedChunk(
                "42:3", "年假制度", 7L, 42L, "handbook.pdf", 5, 3, 0.91d);

        assertThat(chunk.id()).isEqualTo("42:3");
        assertThat(chunk.text()).isEqualTo("年假制度");
        assertThat(chunk.knowledgeBaseId()).isEqualTo(7L);
        assertThat(chunk.documentId()).isEqualTo(42L);
        assertThat(chunk.fileName()).isEqualTo("handbook.pdf");
        assertThat(chunk.pageNumber()).isEqualTo(5);
        assertThat(chunk.chunkIndex()).isEqualTo(3);
        assertThat(chunk.score()).isEqualTo(0.91d);
    }

    @Test
    void vectorIndexPortExposesReplaceSearchAndDelete() {
        VectorIndexPort port = new RecordingVectorIndex();
        DocumentChunk chunk = new DocumentChunk("42:0", "text", 7L, 42L, "a.txt", null, 0);

        port.replaceDocument(42L, List.of(chunk));
        List<RetrievedChunk> found = port.search(7L, new float[] {1f, 0f}, 3, 0.5d);
        port.deleteDocument(42L);

        assertThat(found).singleElement().extracting(RetrievedChunk::id).isEqualTo("42:0");
    }

    private static final class RecordingVectorIndex implements VectorIndexPort {
        @Override
        public void replaceDocument(long documentId, List<DocumentChunk> chunks) {}

        @Override
        public List<RetrievedChunk> search(
                long knowledgeBaseId, float[] query, int topK, double threshold) {
            return List.of(new RetrievedChunk("42:0", "text", 7L, 42L, "a.txt", null, 0, 1d));
        }

        @Override
        public void deleteDocument(long documentId) {}
    }
}
