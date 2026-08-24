// 대시보드 카테고리별 명예의 전당 Top3 항목 - 원문(content)은 프론트에서 stripSlackMarkdown 후 표시
package com.skalahub.dto;

import java.time.LocalDateTime;

public record DashboardHallOfFameEntryDto(
        Long postId, String userName, Boolean isInstructor, String content, Integer reactionCount, LocalDateTime createdAt) {
}
