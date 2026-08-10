// 관리자 링크 모음 수동 수정 요청 - null인 필드는 변경하지 않음
package com.skalahub.dto;

public record AdminLinkUpdateRequest(String url, String title, String source, Boolean hidden) {
}
