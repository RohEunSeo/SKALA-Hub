// 마이페이지 카테고리 필터 탭 옆에 표시할 카테고리/태그별 개수 (탭별로 다름)
package com.skalahub.dto;

import java.util.List;

public record MyPageCategoryCountsResponse(List<CategoryCountDto> categoryCounts, List<CategoryCountDto> tagCounts) {
}
