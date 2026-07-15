package com.brainos.rag.api;

import com.brainos.auth.security.UserPrincipal;
import com.brainos.rag.application.ChatStreamEvent;
import com.brainos.rag.application.RagChatService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ChatStreamController {

    private final RagChatService chat;

    public ChatStreamController(RagChatService chat) {
        this.chat = chat;
    }

    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> ask(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatAskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return chat.ask(sessionId, principal.userId(), request.question())
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder(event)
                        .event(event.type())
                        .build());
    }
}
