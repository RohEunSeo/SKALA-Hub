// 홈 화면 요약 (전체 게시글 수 / 오늘 새 글 수 / 마지막 동기화 / 기수 D일째 / 가입 사용자 수 / 카테고리별 글 수)
package com.skalahub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HomeSummaryResponse(
        long totalPostCount,
        long todayNewPostCount,
        LocalDateTime lastSyncedAt,
        long cohortDay,
        long cohortWeek,
        long userCount,
        List<CategoryCountDto> categoryCounts,
        List<CategoryCountDto> tagCounts) {
}
