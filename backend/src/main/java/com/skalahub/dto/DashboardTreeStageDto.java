// 대시보드 "우리가 함께 키운 SKALA Hub" 나무 성장 단계 - 누적 게시글 수 기준
package com.skalahub.dto;

public record DashboardTreeStageDto(
        String emoji, String label, long totalPostCount, Long nextThreshold, double progressPct, long barMax) {
}
