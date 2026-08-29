// 커리큘럼 탭에서 게시글 제외/복원 (원본 게시글은 변경되지 않음)
package com.skalahub.dto;

public record AdminCurriculumExcludeRequest(Boolean excluded) {}
