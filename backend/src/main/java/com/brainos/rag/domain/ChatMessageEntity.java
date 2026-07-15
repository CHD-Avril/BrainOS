package com.brainos.rag.domain;

public final class ChatMessageEntity {

    private Long id;
    private final long sessionId;
    private final String role;
    private final String content;
    private final String citationsJson;

    public ChatMessageEntity(
            Long id, long sessionId, String role, String content, String citationsJson) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
    }

    public Long getId() { return id; }
    public long getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getCitationsJson() { return citationsJson; }
}
