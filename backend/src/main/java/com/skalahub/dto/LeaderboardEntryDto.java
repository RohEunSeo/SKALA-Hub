// 순위보드 한 항목 (게시글 + 해당 지표 수치)
package com.skalahub.dto;

public record LeaderboardEntryDto(PostResponse post, long count) {
}
