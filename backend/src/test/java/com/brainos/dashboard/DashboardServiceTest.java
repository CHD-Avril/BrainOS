package com.brainos.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private final DashboardMapper mapper = mock(DashboardMapper.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(mapper, clock);
    }

    @Test
    void returnsFourDatabaseSummaryMetrics() {
        DashboardSummary expected = new DashboardSummary(2, 4, 31, 8);
        when(mapper.summary()).thenReturn(expected);

        assertThat(service.summary()).isEqualTo(expected);
    }

    @Test
    void sevenDayTrendIncludesNaturalDatesWithNoQuestions() {
        LocalDate today = LocalDate.of(2026, 7, 14);
        when(mapper.countQuestionsByDate(today.minusDays(6), today))
                .thenReturn(List.of(new DailyCount(today.minusDays(1), 3)));

        List<DailyCount> trend = service.trend(7);

        assertThat(trend).hasSize(7);
        assertThat(trend)
                .extracting(DailyCount::date)
                .containsExactly(
                        LocalDate.of(2026, 7, 8),
                        LocalDate.of(2026, 7, 9),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 11),
                        LocalDate.of(2026, 7, 12),
                        LocalDate.of(2026, 7, 13),
                        LocalDate.of(2026, 7, 14));
        assertThat(trend.get(5)).isEqualTo(new DailyCount(today.minusDays(1), 3));
        assertThat(trend.stream().mapToLong(DailyCount::count).sum()).isEqualTo(3);
    }

    @Test
    void recentDocumentsDelegatesBoundedLimitToAggregationQuery() {
        when(mapper.recentDocuments(5)).thenReturn(List.of());

        assertThat(service.recentDocuments(5)).isEmpty();

        verify(mapper).recentDocuments(5);
    }
}
