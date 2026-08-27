// 관리자 게시글 수동 수정 요청 - null인 필드는 변경하지 않음
package com.skalahub.dto;

import java.util.List;

public record AdminPostUpdateRequest(
        String category,
        List<String> tags,
        Boolean isPinned,
        Boolean isExcludedFromRanking,
        String content,
        Boolean isDeleted) {
}
