// 개인 알림 응답
package com.skalahub.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id, String type, String title, Long postId, boolean isRead, LocalDateTime createdAt) {
}
