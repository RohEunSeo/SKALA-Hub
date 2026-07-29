// 홈 화면 요약 API (누구나 접근 가능 - 개인화 데이터 없음)
package com.skalahub.controller;

import com.skalahub.dto.HomeLeaderboardResponse;
import com.skalahub.dto.HomeSummaryResponse;
import com.skalahub.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/api/home/summary")
    public HomeSummaryResponse getSummary() {
        return homeService.getSummary();
    }

    @GetMapping("/api/home/leaderboard")
    public HomeLeaderboardResponse getLeaderboard(@RequestParam(defaultValue = "all") String period) {
        return homeService.getLeaderboard(period);
    }
}
