// 커리큘럼 탭 게시글 추가 / 카테고리 변경 요청 (upsert - 이미 등록된 게시글이면 stage/subCategory만 갱신)
package com.skalahub.dto;

public record AdminCurriculumUpsertRequest(Long postId, String stage, String subCategory) {}
