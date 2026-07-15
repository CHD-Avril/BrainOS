package com.brainos.rag.retrieval;

public record CitationCandidate(
        long knowledgeBaseId,
        long documentId,
        String chunkId,
        String fileName,
        Integer pageNumber,
        int chunkIndex,
        String snippet,
        double score) {}
