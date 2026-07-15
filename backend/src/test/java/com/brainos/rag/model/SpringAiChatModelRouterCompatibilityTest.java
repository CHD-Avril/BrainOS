package com.brainos.rag.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiChatModelRouterCompatibilityTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = (chunk("模拟回答", null) + chunk("完成", null)
                            + chunk("", "stop") + "data: [DONE]\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void consumesOpenAiCompatibleServerSentEvents() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        AiChatProperties.Provider provider = new AiChatProperties.Provider(baseUrl, "qwen-plus", "test-key");
        SpringAiChatModelRouter router = new SpringAiChatModelRouter(new AiChatProperties(provider, provider));

        List<String> parts = router.stream(ChatModelType.QWEN, new Prompt("问题"))
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(parts).containsExactly("模拟回答", "完成");
    }

    private static String chunk(String content, String finishReason) {
        String delta = content.isEmpty() ? "{}" : "{\"role\":\"assistant\",\"content\":\"" + content + "\"}";
        String finish = finishReason == null ? "null" : "\"" + finishReason + "\"";
        return "data: {\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1784044800,\"model\":\"qwen-plus\",\"choices\":[{\"index\":0,"
                + "\"delta\":" + delta + ",\"logprobs\":null,\"finish_reason\":" + finish + "}]}\n\n";
    }
}
