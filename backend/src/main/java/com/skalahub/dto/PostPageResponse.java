// 게시글 목록 페이지 응답 (마지막 동기화 시각 포함)
package com.skalahub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostPageResponse(
        List<PostResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LocalDateTime lastSyncedAt) {
}
