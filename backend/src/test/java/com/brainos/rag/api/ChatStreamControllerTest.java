package com.brainos.rag.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brainos.auth.domain.UserRole;
import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.common.api.GlobalExceptionHandler;
import com.brainos.rag.application.ChatStreamEvent;
import com.brainos.rag.application.RagChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatStreamControllerTest {

    @Test
    void preservesProtocolEventNamesInSseEnvelope() {
        RagChatService chat = mock(RagChatService.class);
        ChatStreamController controller = new ChatStreamController(chat);
        UserPrincipal principal = new UserPrincipal(9L, "baron", UserRole.USER);
        when(chat.ask(11L, 9L, "年假几天？"))
                .thenReturn(Flux.just(
                        ChatStreamEvent.start(),
                        ChatStreamEvent.delta("5天"),
                        ChatStreamEvent.citations(List.of()),
                        ChatStreamEvent.done()));

        StepVerifier.create(controller.ask(11L, new ChatAskRequest("年假几天？"), principal))
                .assertNext(event -> assertThat(event.event()).isEqualTo("start"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("delta"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("citations"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("done"))
                .verifyComplete();
    }

    @Test
    void foreignSessionReturnsNotFoundBeforeSseStarts() throws Exception {
        RagChatService chat = mock(RagChatService.class);
        ChatStreamController controller = new ChatStreamController(chat);
        UserPrincipal principal = new UserPrincipal(10L, "other", UserRole.USER);
        when(chat.ask(11L, 10L, "年假几天？"))
                .thenThrow(new ApiException(ErrorCode.NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(principal))
                .build();

        mvc.perform(post("/api/v1/chat/sessions/11/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"年假几天？\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static HandlerMethodArgumentResolver principalResolver(UserPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer container,
                    NativeWebRequest request,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
