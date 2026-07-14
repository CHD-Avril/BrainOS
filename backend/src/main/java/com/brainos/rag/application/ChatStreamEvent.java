package com.brainos.rag.application;

import com.brainos.rag.retrieval.CitationCandidate;
import java.util.List;

public record ChatStreamEvent(
        String type, String content, List<CitationCandidate> citations, String message) {

    public ChatStreamEvent {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static ChatStreamEvent start() {
        return new ChatStreamEvent("start", null, List.of(), null);
    }

    public static ChatStreamEvent delta(String content) {
        return new ChatStreamEvent("delta", content, List.of(), null);
    }

    public static ChatStreamEvent citations(List<CitationCandidate> citations) {
        return new ChatStreamEvent("citations", null, citations, null);
    }

    public static ChatStreamEvent done() {
        return new ChatStreamEvent("done", null, List.of(), null);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, List.of(), message);
    }
}
