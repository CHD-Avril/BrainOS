package com.brainos.rag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSessionRenameRequest(@NotBlank @Size(max = 60) String title) {}
