package com.brainos.auth.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.application.AuthenticationFailedException;
import com.brainos.auth.token.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void loginFailureUsesGenericUnauthorizedEnvelope() throws Exception {
        assertUnauthorized("/login-failure");
    }

    @Test
    void refreshFailureUsesGenericUnauthorizedEnvelope() throws Exception {
        assertUnauthorized("/refresh-failure");
    }

    private void assertUnauthorized(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @RestController
    static class TestController {

        @GetMapping("/login-failure")
        void loginFailure() {
            throw new AuthenticationFailedException();
        }

        @GetMapping("/refresh-failure")
        void refreshFailure() {
            throw new InvalidRefreshTokenException();
        }
    }
}
