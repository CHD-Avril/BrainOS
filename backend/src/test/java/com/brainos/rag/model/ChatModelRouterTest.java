package com.brainos.rag.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatModelRouterTest {

    @Test
    void routesOnlyNamedQwenAndDeepSeekModels() {
        SpringAiChatModelRouter router = new SpringAiChatModelRouter(Map.of(
                ChatModelType.QWEN, prompt -> Flux.just("千问:" + prompt.getContents()),
                ChatModelType.DEEPSEEK, prompt -> Flux.just("深度:" + prompt.getContents())));

        StepVerifier.create(router.stream(ChatModelType.QWEN, new Prompt("制度")))
                .expectNext("千问:制度")
                .verifyComplete();
        StepVerifier.create(router.stream(ChatModelType.DEEPSEEK, new Prompt("制度")))
                .expectNext("深度:制度")
                .verifyComplete();
        assertThat(ChatModelType.valueOf("QWEN")).isEqualTo(ChatModelType.QWEN);
    }
}
