package com.brainos.rag.retrieval;

import com.brainos.ai.embedding.EmbeddingPort;
import com.brainos.document.indexing.RetrievedChunk;
import com.brainos.document.indexing.VectorIndexPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class ChromaRagRetriever implements RagRetriever {

    private final EmbeddingPort embeddings;
    private final VectorIndexPort vectors;

    public ChromaRagRetriever(EmbeddingPort embeddings, VectorIndexPort vectors) {
        this.embeddings = embeddings;
        this.vectors = vectors;
    }

    @Override
    public List<CitationCandidate> retrieve(
            long knowledgeBaseId, String question, int topK, double threshold) {
        float[] query = embeddings.embed(question);
        return vectors.search(knowledgeBaseId, query, topK, threshold).stream()
                .map(ChromaRagRetriever::citation)
                .toList();
    }

    private static CitationCandidate citation(RetrievedChunk chunk) {
        return new CitationCandidate(
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.id(),
                chunk.fileName(),
                chunk.pageNumber(),
                chunk.chunkIndex(),
                chunk.text(),
                chunk.score());
    }
}
