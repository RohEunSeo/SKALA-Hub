// 대시보드 탭 요약 API (로그인한 사용자면 누구나 접근 가능 - /api/dashboard/**라 SecurityConfig에서 인증만 요구)
package com.skalahub.controller;

import com.skalahub.dto.DashboardSummaryResponse;
import com.skalahub.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
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
