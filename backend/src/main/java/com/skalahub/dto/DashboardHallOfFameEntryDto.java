// 대시보드 카테고리별 명예의 전당 Top3 항목 - aiTitle이 있으면 그걸, 없으면 프론트에서 content를
// stripSlackMarkdown 후 잘라서 대신 표시
package com.skalahub.dto;

import java.time.LocalDateTime;

public record DashboardHallOfFameEntryDto(
        Long postId,
        String userName,
        Boolean isInstructor,
        String content,
        String aiTitle,
        Integer reactionCount,
        LocalDateTime createdAt) {
}
