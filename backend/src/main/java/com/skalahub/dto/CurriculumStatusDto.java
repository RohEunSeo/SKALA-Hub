// 피드 탭에서 게시글이 이미 커리큘럼에 등록되어 있는지 표시하기 위한 최소 정보(배치 조회용)
package com.skalahub.dto;

public record CurriculumStatusDto(Long postId, String stage, String subCategory) {}
