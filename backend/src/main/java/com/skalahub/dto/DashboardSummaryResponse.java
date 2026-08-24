// 대시보드 탭 요약 (관리자 전용, 마이페이지 폴링용 단일 응답)
package com.skalahub.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        LocalDate courseStart,
        LocalDate courseEnd,
        long daysLeft,
        long totalPostCount,
        DashboardTreeStageDto treeStage,
        List<DashboardHeatmapDayDto> heatmap,
        DashboardHeatmapStatsDto heatmapStats,
        List<DashboardSignupPointDto> signupTrend,
        List<DashboardLoginBarDto> loginByClass,
        List<DashboardCategoryDistDto> categoryDist,
        // 카테고리 값(예: "학습자료") -> Top3 목록
        Map<String, List<DashboardHallOfFameEntryDto>> hallOfFame) {
}
