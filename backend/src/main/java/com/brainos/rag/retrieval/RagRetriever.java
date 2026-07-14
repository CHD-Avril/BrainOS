package com.brainos.rag.retrieval;

import java.util.List;

public interface RagRetriever {

    List<CitationCandidate> retrieve(
            long knowledgeBaseId, String question, int topK, double threshold);
}
