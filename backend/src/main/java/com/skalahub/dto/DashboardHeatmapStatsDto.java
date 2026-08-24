// 대시보드 히트맵 부가 통계 (오늘 기준 - 미래 날짜는 집계에서 제외)
package com.skalahub.dto;

public record DashboardHeatmapStatsDto(
        String maxDayLabel, long maxDayCount, double avgPerDay, String bestWeekday, long streakDays) {
}
