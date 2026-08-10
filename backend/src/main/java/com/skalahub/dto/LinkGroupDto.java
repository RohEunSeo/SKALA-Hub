// 링크 모음 탭 카드 - 같은 URL을 올린 게시글을 전부 묶은 그룹 하나
// reactionCount/replyCount는 그룹 내 반응수가 가장 높은 "대표 게시글"의 실제 값(합산 아님)
package com.skalahub.dto;

import java.util.List;

public record LinkGroupDto(
        String url,
        String titleLink,
        String title,
        String text,
        String imageUrl,
        String serviceName,
        List<String> creators,
        Integer reactionCount,
        Integer replyCount,
        String category,
        List<LinkGroupPostDto> posts) {
}
