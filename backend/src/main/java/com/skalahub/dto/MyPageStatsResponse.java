// 마이페이지 통계 (올린 글 / 받은 이모지 / 저장한 글 / 반응한 글)
package com.skalahub.dto;

public record MyPageStatsResponse(long postCount, long reactionsReceived, long savedCount, long reactedCount) {
}
