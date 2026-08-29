// 커리큘럼 탭 게시글 응답 - 게시글 정보 + 커리큘럼 분류 메타데이터
package com.skalahub.dto;

import java.time.LocalDateTime;

public record CurriculumPostResponse(
        PostResponse post, String stage, String subCategory, String addedBy, LocalDateTime createdAt) {}
