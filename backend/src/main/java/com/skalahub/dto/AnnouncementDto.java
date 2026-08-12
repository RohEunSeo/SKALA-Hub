// 전체 공지 응답 - 현재 로그인한 유저 기준 읽음 여부(isRead) 포함
package com.skalahub.dto;

import java.time.LocalDateTime;

public record AnnouncementDto(
        Long id,
        String badgeType,
        String title,
        String content,
        String linkPath,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isRead) {
}
