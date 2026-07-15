package com.brainos.rag.application;

import com.brainos.rag.retrieval.CitationCandidate;
import java.util.List;

public record RagAnswerPlan(List<CitationCandidate> citations, String fallback) {

    public RagAnswerPlan {
        citations = List.copyOf(citations);
    }

    public static RagAnswerPlan grounded(List<CitationCandidate> citations) {
        return new RagAnswerPlan(citations, null);
    }

    public static RagAnswerPlan fallback(String message) {
        return new RagAnswerPlan(List.of(), message);
    }

    public boolean isFallback() {
        return fallback != null;
    }
}
