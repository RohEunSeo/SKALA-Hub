// 관리자 공지 작성/수정 요청 (수정 시에도 동일한 바디 사용)
package com.skalahub.dto;

public record AnnouncementCreateRequest(
        String badgeType,
        String title,
        String content,
        String linkLabel,
        String linkPath,
        String linkLabel2,
        String linkPath2) {
}
