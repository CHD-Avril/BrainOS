package com.brainos.knowledge.domain;

public final class KnowledgeBase {

    private Long id;
    private final String name;
    private final String description;
    private final long createdBy;

    public KnowledgeBase(Long id, String name, String description, long createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public long createdBy() {
        return createdBy;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("Knowledge base id is already assigned");
        }
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getCreatedBy() {
        return createdBy;
    }
}
