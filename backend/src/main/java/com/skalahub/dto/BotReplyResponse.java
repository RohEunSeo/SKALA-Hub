// 관리자가 슬랙 봇이 남긴 댓글을 목록으로 확인/관리할 때 쓰는 응답
package com.skalahub.dto;

import java.time.LocalDateTime;

// status: "success" | "failure" | "pending" - pending은 실제 슬랙 댓글이 아직 없는 상태라 id·ts가 null
public record BotReplyResponse(
        Long id,
        String ts,
        String content,
        LocalDateTime createdAt,
        Long postId,
        String postAuthor,
        String postPreview,
        String status) {
}
