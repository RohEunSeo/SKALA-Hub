// 게시글 작성자 본인 또는 관리자가 AI 제목을 직접 고쳐 쓸 때 사용
package com.skalahub.dto;

public record PostTitleUpdateRequest(String aiTitle) {
}
