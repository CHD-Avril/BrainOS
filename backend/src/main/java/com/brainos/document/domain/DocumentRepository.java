package com.brainos.document.domain;

import java.util.Optional;

public interface DocumentRepository {

    boolean existsByKnowledgeBaseAndSha256(long knowledgeBaseId, String sha256);

    void create(KnowledgeDocument document);

    Optional<DocumentView> findById(long id);
}
