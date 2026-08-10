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
        List<CategoryCountDto> tagCounts,
        // 링크 모음 탭 카테고리 칩 - 게시글 수가 아니라 attachments 배열 원소 총합
        long totalLinkCount,
        List<CategoryCountDto> linkCategoryCounts,
        // 링크 모음 탭 하위 분류(🗂️ 분류) 필터용 - linkCategoryCounts와 동일한 개념의 태그별 링크 수
        List<CategoryCountDto> linkTagCounts) {
}
