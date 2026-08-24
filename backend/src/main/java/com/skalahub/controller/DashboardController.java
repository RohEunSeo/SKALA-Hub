// 대시보드 탭 요약 API (관리자 전용 - /api/admin/**이라 SecurityConfig에서 ROLE_ADMIN만 접근 가능)
package com.skalahub.controller;

import com.skalahub.dto.DashboardSummaryResponse;
import com.skalahub.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }
}
