package com.brainos.dashboard;

import com.brainos.common.api.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.success(dashboard.summary());
    }

    @GetMapping("/trends")
    public ApiResponse<List<DailyCount>> trends(
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(dashboard.trend(days));
    }

    @GetMapping("/recent-documents")
    public ApiResponse<List<RecentDocument>> recentDocuments(
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success(dashboard.recentDocuments(limit));
    }
}
