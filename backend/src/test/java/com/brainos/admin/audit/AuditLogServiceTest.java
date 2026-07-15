package com.brainos.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.common.api.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditLogServiceTest {

    private final AuditMapper mapper = mock(AuditMapper.class);
    private final AuditLogService service = new AuditLogService(mapper);

    @Test
    void normalizesFiltersAndReturnsStablePage() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-15T00:00:00Z");
        AuditLogView row = new AuditLogView(
                3L, 1L, "admin", "USER_CREATE", "USER", "21", "SUCCESS", "创建用户", to);
        when(mapper.count(1L, "admin", "USER_CREATE", from, to)).thenReturn(1L);
        when(mapper.findPage(1L, "admin", "USER_CREATE", from, to, 20, 20L))
                .thenReturn(List.of(row));

        var result = service.list(1L, " Admin ", " user_create ", from, to, 2, 20);

        assertThat(result.items()).containsExactly(row);
        assertThat(result.total()).isOne();
        verify(mapper).findPage(1L, "admin", "USER_CREATE", from, to, 20, 20L);
    }

    @Test
    void rejectsReversedTimeRange() {
        assertThatThrownBy(() -> service.list(
                        null,
                        null,
                        null,
                        Instant.parse("2026-07-15T00:00:00Z"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        1,
                        20))
                .isInstanceOf(ApiException.class);
    }
}
