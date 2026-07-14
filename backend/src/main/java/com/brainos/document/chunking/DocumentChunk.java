package com.brainos.document.chunking;

import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentChunk(
    String id,
    String text,
    Long knowledgeBaseId,
    Long documentId,
    String fileName,
    Integer pageNumber,
    int chunkIndex
) {

    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("documentId", documentId);
        metadata.put("fileName", fileName);
        metadata.put("pageNumber", pageNumber == null ? -1 : pageNumber);
        metadata.put("chunkIndex", chunkIndex);
        return Map.copyOf(metadata);
    }
}
