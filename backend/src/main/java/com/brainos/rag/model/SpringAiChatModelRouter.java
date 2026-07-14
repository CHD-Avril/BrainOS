package com.brainos.rag.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class SpringAiChatModelRouter implements ChatModelRouter {

    private final AiChatProperties properties;
    private final Map<ChatModelType, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<ChatModelType, Function<Prompt, Flux<String>>> testRoutes;

    @Autowired
    public SpringAiChatModelRouter(AiChatProperties properties) {
        this.properties = properties;
        this.testRoutes = null;
    }

    SpringAiChatModelRouter(Map<ChatModelType, Function<Prompt, Flux<String>>> routes) {
        this.properties = null;
        this.testRoutes = new EnumMap<>(routes);
    }

    @Override
    public Flux<String> stream(ChatModelType type, Prompt prompt) {
        if (type == null || prompt == null) {
            return Flux.error(new IllegalArgumentException("chat model and prompt are required"));
        }
        if (testRoutes != null) {
            Function<Prompt, Flux<String>> route = testRoutes.get(type);
            return route == null
                    ? Flux.error(new IllegalArgumentException("unsupported chat model"))
                    : route.apply(prompt);
        }
        return model(type).stream(prompt)
                .handle((response, sink) -> {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) sink.next(text);
                });
    }

    private ChatModel model(ChatModelType type) {
        return models.computeIfAbsent(type, this::createModel);
    }

    private ChatModel createModel(ChatModelType type) {
        AiChatProperties.Provider provider = type == ChatModelType.QWEN
                ? properties.qwen()
                : properties.deepseek();
        if (provider == null || provider.apiKey() == null || provider.apiKey().isBlank()) {
            throw new ChatModelNotConfiguredException(type);
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(stripTrailingSlash(provider.baseUrl()))
                .apiKey(provider.apiKey())
                .completionsPath("/chat/completions")
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(provider.model())
                .temperature(0.2d)
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
