// 피드 게시글 응답
package com.skalahub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long id,
        String userName,
        String userAvatarUrl,
        Boolean isInstructor,
        String content,
        String category,
        List<String> tags,
        Integer reactionCount,
        Integer replyCount,
        Integer bookmarkCount,
        Boolean isPinned,
        Boolean isExcludedFromRanking,
        Boolean isEdited,
        LocalDateTime createdAt,
        String slackPermalink,
        List<LinkPreviewDto> attachments,
        List<FileAttachmentDto> files,
        // 링크 모음 탭용 - 슬랙이 미리보기 카드를 만들어준 링크(attachments) + 본문에 텍스트로만 붙은 링크를
        // 합쳐서 중복 없이 나열한 목록(리치 미리보기가 있으면 그걸, 없으면 URL/라벨만으로 구성)
        List<LinkPreviewDto> links) {
}
