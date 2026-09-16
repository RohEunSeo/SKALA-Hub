// 관리자 "구글 계정 연동 관리" 화면 - 페이지네이션 목록 응답
package com.skalahub.dto;

import java.util.List;

public record GoogleLinkPageResponse(
        List<GoogleLinkResponse> content, int page, int size, long totalElements, int totalPages) {
}
