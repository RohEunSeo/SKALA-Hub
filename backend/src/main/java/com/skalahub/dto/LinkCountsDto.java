// 링크 모음 탭 카테고리/분류 칩 개수 - 게시글 수가 아니라 URL로 그룹핑된 카드 수 기준(중복 링크는 1개로만 셈)
package com.skalahub.dto;

import java.util.List;

public record LinkCountsDto(long total, List<CategoryCountDto> categoryCounts, List<CategoryCountDto> tagCounts) {
}
