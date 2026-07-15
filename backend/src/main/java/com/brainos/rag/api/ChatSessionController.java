package com.brainos.rag.api;

import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiResponse;
import com.brainos.rag.application.ChatSessionService;
import com.brainos.rag.domain.ChatSessionDetail;
import com.brainos.rag.domain.ChatSessionView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ChatSessionController {

    private final ChatSessionService sessions;

    public ChatSessionController(ChatSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public ApiResponse<ChatSessionView> create(
            @Valid @RequestBody ChatSessionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(sessions.create(
                request.knowledgeBaseId(), request.chatModel(), principal.userId()));
    }

    @GetMapping
    public ApiResponse<List<ChatSessionView>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(sessions.list(principal.userId()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionDetail> get(
            @PathVariable long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(sessions.get(sessionId, principal.userId()));
    }

    @PutMapping("/{sessionId}")
    public ApiResponse<ChatSessionView> rename(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatSessionRenameRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                sessions.rename(sessionId, principal.userId(), request.title()));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
            @PathVariable long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        sessions.delete(sessionId, principal.userId());
        return ApiResponse.success(null);
    }
}
