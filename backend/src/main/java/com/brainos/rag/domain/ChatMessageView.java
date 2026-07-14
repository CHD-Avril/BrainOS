package com.brainos.rag.domain;

import com.brainos.rag.retrieval.CitationCandidate;
import java.time.Instant;
import java.util.List;

public record ChatMessageView(
        long id,
        long sessionId,
        String role,
        String content,
        List<CitationCandidate> citations,
        Instant createdAt) {

    public ChatMessageView {
        citations = List.copyOf(citations);
    }
}
