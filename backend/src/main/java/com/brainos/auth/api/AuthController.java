package com.brainos.auth.api;

import com.brainos.auth.application.AuthService;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserSummary;
import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiException;
import com.brainos.common.api.ApiResponse;
import com.brainos.common.api.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(TokenResponse.from(
                authService.login(request.username(), request.password())));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(TokenResponse.from(
                authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        UserSummary user = users.findById(principal.userId())
                .filter(account -> account.isEnabled())
                .map(account -> account.toSummary())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.success(user);
    }
}
