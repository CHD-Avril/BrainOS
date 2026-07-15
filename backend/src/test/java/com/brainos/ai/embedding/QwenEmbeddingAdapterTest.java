package com.brainos.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class QwenEmbeddingAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsBatchToConfiguredOpenAiCompatibleEndpoint() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"object":"list","data":[
                          {"object":"embedding","index":1,"embedding":[0.0,1.0,0.25]},
                          {"object":"embedding","index":0,"embedding":[1.0,0.0,0.5]}
                        ],"model":"text-embedding-v4","usage":{"prompt_tokens":2,"total_tokens":2}}
                        """));
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/compatible-mode/v1").toString(),
                "text-embedding-v4",
                1024,
                "test-secret"));

        List<float[]> vectors = adapter.embedAll(List.of("制度", "请假"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f, 0.5f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 1.0f, 0.25f);
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/compatible-mode/v1/embeddings");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-secret");
        assertThat(request.getBody().readUtf8())
                .contains("\"model\":\"text-embedding-v4\"")
                .contains("\"dimensions\":1024")
                .contains("\"input\":[\"制度\",\"请假\"]")
                .doesNotContain("test-secret");
    }

    @Test
    void splitsLargeInputIntoProviderSizedBatchesAndPreservesOrder() throws InterruptedException {
        server.enqueue(jsonResponse(embeddingResponse(10, 0)));
        server.enqueue(jsonResponse(embeddingResponse(10, 10)));
        server.enqueue(jsonResponse(embeddingResponse(9, 20)));
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, "test-secret"));
        List<String> inputs = IntStream.range(0, 29)
                .mapToObj(index -> "切片-" + index)
                .toList();

        List<float[]> vectors = adapter.embedAll(inputs);

        assertThat(vectors).hasSize(29);
        for (int index = 0; index < vectors.size(); index++) {
            assertThat(vectors.get(index)).containsExactly((float) index);
        }
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8())
                .contains("\"切片-0\"")
                .contains("\"切片-9\"")
                .doesNotContain("\"切片-10\"");
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8())
                .contains("\"切片-10\"")
                .contains("\"切片-19\"")
                .doesNotContain("\"切片-20\"");
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8())
                .contains("\"切片-20\"")
                .contains("\"切片-28\"");
    }

    @Test
    void rejectsInvalidResponseIndexesInsteadOfMisaligningVectors() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"object":"list","data":[
                          {"object":"embedding","index":0,"embedding":[1.0,0.0]},
                          {"object":"embedding","index":0,"embedding":[0.0,1.0]}
                        ],"model":"text-embedding-v4","usage":{"prompt_tokens":2,"total_tokens":2}}
                        """));
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, "test-secret"));

        assertThatThrownBy(() -> adapter.embedAll(List.of("制度", "请假")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding response index mismatch");
    }

    @Test
    void embedsSingleTextThroughSameContract() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"object":"list","data":[
                          {"object":"embedding","index":0,"embedding":[0.2,0.8]}
                        ],"model":"text-embedding-v4","usage":{"prompt_tokens":1,"total_tokens":1}}
                        """));
        EmbeddingPort adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, "test-secret"));

        assertThat(adapter.embed("企业知识")).containsExactly(0.2f, 0.8f);
    }

    @Test
    void blankApiKeyFailsFastWithoutCallingNetworkOrLeakingSecret() {
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, " "));

        assertThatThrownBy(() -> adapter.embed("不应发送"))
                .isInstanceOf(EmbeddingNotConfiguredException.class)
                .hasMessage("向量模型未配置")
                .hasMessageNotContaining("test-secret");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void upstreamFailureDoesNotExposeAuthorizationSecret() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"bad request\"}}"));
        String secret = "qwen-secret-must-not-leak";
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, secret));
        Logger retryLogger = (Logger) LoggerFactory.getLogger("org.springframework.ai.retry.RetryUtils");
        boolean originalAdditive = retryLogger.isAdditive();
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        retryLogger.addAppender(events);
        retryLogger.setAdditive(false);
        try {
            assertThatThrownBy(() -> adapter.embed("制度"))
                    .hasMessageNotContaining(secret);
            assertThat(events.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message).doesNotContain(secret));
        } finally {
            retryLogger.detachAppender(events);
            retryLogger.setAdditive(originalAdditive);
            events.stop();
        }
    }

    @Test
    void rejectsBlankInputBeforeNetworkCall() {
        QwenEmbeddingAdapter adapter = new QwenEmbeddingAdapter(new QwenEmbeddingProperties(
                server.url("/v1").toString(), "text-embedding-v4", 1024, "test-secret"));

        assertThatThrownBy(() -> adapter.embedAll(List.of("valid", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String embeddingResponse(int count, int valueOffset) {
        StringBuilder data = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (!data.isEmpty()) {
                data.append(',');
            }
            data.append("{\"object\":\"embedding\",\"index\":")
                    .append(index)
                    .append(",\"embedding\":[")
                    .append(valueOffset + index)
                    .append("]}");
        }
        return "{\"object\":\"list\",\"data\":[" + data
                + "],\"model\":\"text-embedding-v4\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }
}
