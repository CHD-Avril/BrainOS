package com.brainos.rag.domain;

import java.util.List;

public record ChatSessionDetail(ChatSessionView session, List<ChatMessageView> messages) {
    public ChatSessionDetail {
        messages = List.copyOf(messages);
    }
}
