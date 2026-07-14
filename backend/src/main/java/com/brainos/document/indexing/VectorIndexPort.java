package com.brainos.document.indexing;

import com.brainos.document.chunking.DocumentChunk;
import java.util.List;

public interface VectorIndexPort {

    void replaceDocument(long documentId, List<DocumentChunk> chunks);

    List<RetrievedChunk> search(
            long knowledgeBaseId, float[] query, int topK, double threshold);

    void deleteDocument(long documentId);
}
