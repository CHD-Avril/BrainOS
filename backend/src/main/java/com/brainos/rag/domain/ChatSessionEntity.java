package com.brainos.rag.domain;

import com.brainos.rag.model.ChatModelType;

public final class ChatSessionEntity {

    private Long id;
    private final String title;
    private final long knowledgeBaseId;
    private final ChatModelType chatModel;
    private final long userId;

    public ChatSessionEntity(
            Long id, String title, long knowledgeBaseId, ChatModelType chatModel, long userId) {
        this.id = id;
        this.title = title;
        this.knowledgeBaseId = knowledgeBaseId;
        this.chatModel = chatModel;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public long getKnowledgeBaseId() { return knowledgeBaseId; }
    public ChatModelType getChatModel() { return chatModel; }
    public long getUserId() { return userId; }

    public void assignId(long id) {
        if (this.id != null) throw new IllegalStateException("Session id is already assigned");
        this.id = id;
    }
}
