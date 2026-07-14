package com.brainos.knowledge.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotNull @Size(min = 2, max = 60) String name,
        @Size(max = 500) String description) {

    public KnowledgeBaseCreateRequest {
        if (name != null) {
            name = name.trim();
        }
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}
