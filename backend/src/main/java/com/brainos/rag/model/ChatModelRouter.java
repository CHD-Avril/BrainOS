package com.brainos.rag.model;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface ChatModelRouter {

    Flux<String> stream(ChatModelType model, Prompt prompt);
}
