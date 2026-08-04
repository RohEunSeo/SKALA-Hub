// 관리자가 슬랙 봇이 남긴 댓글을 목록으로 확인/관리할 때 쓰는 응답
package com.skalahub.dto;

import java.time.LocalDateTime;

public record BotReplyResponse(
        Long id,
        String ts,
        String content,
        LocalDateTime createdAt,
        Long postId,
        String postPreview) {
}
