package com.brainos.document.domain;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    boolean existsByKnowledgeBaseAndSha256(long knowledgeBaseId, String sha256);

    void create(KnowledgeDocument document);

    Optional<DocumentView> findById(long id);

    List<DocumentView> findAllByKnowledgeBaseId(long knowledgeBaseId);

    int markParsing(long id);

    int markIndexing(long id);

    int markReady(long id, int chunkCount);

    int markFailed(long id, String failureReason);

    int delete(long id);
}
