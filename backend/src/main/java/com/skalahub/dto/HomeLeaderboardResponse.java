// 홈 화면 순위보드 (반응/댓글/저장 TOP3)
package com.skalahub.dto;

import java.util.List;

public record HomeLeaderboardResponse(
        List<LeaderboardEntryDto> topReactions,
        List<LeaderboardEntryDto> topComments,
        List<LeaderboardEntryDto> topSaves) {
}
