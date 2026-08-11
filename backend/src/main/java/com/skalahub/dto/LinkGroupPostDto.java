// 링크 모음 카드 하나(=URL 하나)에 딸린 게시글 목록 항목 - "게시글 N개 보러가기" 펼치기용
package com.skalahub.dto;

import java.time.LocalDateTime;

public record LinkGroupPostDto(
        Long id,
        String userName,
        String userAvatarUrl,
        Integer reactionCount,
        Integer replyCount,
        LocalDateTime createdAt) {
}
