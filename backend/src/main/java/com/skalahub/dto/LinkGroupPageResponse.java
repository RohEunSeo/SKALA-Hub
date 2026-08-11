// 링크 모음 탭 페이지 응답
package com.skalahub.dto;

import java.util.List;

public record LinkGroupPageResponse(
        List<LinkGroupDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
