// 대시보드 카테고리별 게시글 분포 (도넛차트) - 카테고리별 개수 + 비율(%)
package com.skalahub.dto;

public record DashboardCategoryDistDto(String category, long count, double pct) {
}
