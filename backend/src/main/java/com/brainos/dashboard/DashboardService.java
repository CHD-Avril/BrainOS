package com.brainos.dashboard;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DashboardService {

    private static final int MAX_TREND_DAYS = 30;
    private static final int MAX_RECENT_DOCUMENTS = 5;

    private final DashboardMapper mapper;
    private final Clock clock;

    public DashboardService(DashboardMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public DashboardSummary summary() {
        return mapper.summary();
    }

    public List<DailyCount> trend(int days) {
        requireRange(days, MAX_TREND_DAYS);
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1L);
        Map<LocalDate, DailyCount> counts = mapper.countQuestionsByDate(startDate, today).stream()
                .collect(Collectors.toMap(DailyCount::date, Function.identity()));
        return IntStream.range(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> counts.getOrDefault(date, new DailyCount(date, 0)))
                .toList();
    }

    public List<RecentDocument> recentDocuments(int limit) {
        requireRange(limit, MAX_RECENT_DOCUMENTS);
        return mapper.recentDocuments(limit);
    }

    private static void requireRange(int value, int maximum) {
        if (value < 1 || value > maximum) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
