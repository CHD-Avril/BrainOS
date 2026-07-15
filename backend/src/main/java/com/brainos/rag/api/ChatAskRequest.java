package com.brainos.rag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatAskRequest(@NotBlank @Size(max = 1000) String question) {}
