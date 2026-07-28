// 스레드 댓글 응답
package com.skalahub.dto;

import java.time.LocalDateTime;

public record ReplyResponse(
        Long id,
        String userName,
        String userAvatarUrl,
        String content,
        LocalDateTime createdAt) {
}
