package com.brainos.knowledge.application;

@FunctionalInterface
public interface KnowledgeBaseCleanupPort {

    void cleanup(long knowledgeBaseId);
}
