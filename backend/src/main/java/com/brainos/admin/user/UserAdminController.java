package com.brainos.admin.user;

import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiResponse;
import com.brainos.common.api.PagedResult;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    public ApiResponse<PagedResult<AdminUserView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(users.list(page, size));
    }

    @PostMapping
    public ApiResponse<AdminUserView> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(users.create(
                request.username(),
                request.displayName(),
                request.password(),
                request.role(),
                principal.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserView> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(users.update(
                id,
                request.displayName(),
                request.role(),
                request.password(),
                principal.userId()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserView> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeUserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                users.changeStatus(id, request.status(), principal.userId()));
    }
}
