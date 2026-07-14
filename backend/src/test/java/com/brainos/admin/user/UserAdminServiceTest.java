package com.brainos.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.admin.audit.AuditRecorder;
import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.common.api.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserAdminServiceTest {

    private final AdminUserMapper users = mock(AdminUserMapper.class);
    private final UserRepository accounts = mock(UserRepository.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final UserAdminService service = new UserAdminService(
            users, accounts, new BCryptPasswordEncoder(12), refreshTokens, audit);

    @Test
    void createsNormalizedUserWithCost12HashAndNoPasswordResponse() {
        doAnswer(invocation -> {
                    invocation.getArgument(0, AdminUserEntity.class).assignId(21L);
                    return null;
                })
                .when(users)
                .create(any(AdminUserEntity.class));
        when(users.findViewById(21L)).thenReturn(Optional.of(view(21L, UserRole.USER, UserStatus.ENABLED)));

        AdminUserView created = service.create(
                "  Alice.Dev  ", " Alice ", "Secure123", UserRole.USER, 1L);

        assertThat(created.username()).isEqualTo("alice.dev");
        var captor = org.mockito.ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(users).create(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice.dev");
        assertThat(captor.getValue().getPasswordHash()).startsWith("$2").contains("$12$");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Secure123");
        verify(audit).record(any());
    }

    @Test
    void cannotDisableLastEnabledAdministrator() {
        when(accounts.findById(1L)).thenReturn(Optional.of(account(1L, UserRole.ADMIN, UserStatus.ENABLED)));
        when(users.countEnabledAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(1L, UserStatus.DISABLED, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("CONFLICT");

        verify(users, never()).updateStatus(anyLong(), any());
    }

    @Test
    void disablingUserRevokesEveryRefreshTokenAndAudits() {
        when(accounts.findById(22L)).thenReturn(Optional.of(account(22L, UserRole.USER, UserStatus.ENABLED)));
        when(users.updateStatus(22L, UserStatus.DISABLED)).thenReturn(1);
        when(users.findViewById(22L)).thenReturn(Optional.of(view(22L, UserRole.USER, UserStatus.DISABLED)));

        AdminUserView disabled = service.changeStatus(22L, UserStatus.DISABLED, 1L);

        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        verify(refreshTokens).revokeAll(22L);
        verify(audit).record(any());
    }

    private static UserAccount account(long id, UserRole role, UserStatus status) {
        return new UserAccount(id, "user" + id, "unused", "User", role, status);
    }

    private static AdminUserView view(long id, UserRole role, UserStatus status) {
        return new AdminUserView(
                id,
                id == 21L ? "alice.dev" : "user" + id,
                "User",
                role,
                status,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
