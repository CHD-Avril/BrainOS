package com.brainos.document.indexing;

public record RetrievedChunk(
        String id,
        String text,
        Long knowledgeBaseId,
        Long documentId,
        String fileName,
        Integer pageNumber,
        int chunkIndex,
        double score) {}
