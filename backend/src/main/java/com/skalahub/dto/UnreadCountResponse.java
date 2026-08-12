// 알림벨 뱃지 카운트 - 전체 공지 안읽음 수 + 개인 알림 안읽음 수
package com.skalahub.dto;

public record UnreadCountResponse(long announcementUnread, long notificationUnread) {
}
