package com.brainos.knowledge.domain;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    List<KnowledgeBaseView> findAll();

    Optional<KnowledgeBaseView> findById(long id);

    boolean existsByName(String name, Long excludedId);

    void create(KnowledgeBase knowledgeBase);

    int update(long id, String name, String description);

    int delete(long id);
}
