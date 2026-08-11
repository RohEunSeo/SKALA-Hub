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
        // 학습자료 하위 유형 배지에 쓸 확정된 값 하나(영상/블로그·글/깃허브) - 학습자료가 아니면 null.
        // 게시글의 tags 목록을 그대로 내려주지 않는 이유는 게시글 하나에 태그가 여러 개 달릴 수 있어
        // "이 링크 하나"의 유형과 어긋날 수 있기 때문 - LinkService가 이미 하나로 확정해서 내려줌
        String typeTag,
        List<LinkGroupPostDto> posts) {
}
