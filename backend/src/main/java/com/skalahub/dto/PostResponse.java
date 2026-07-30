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
        Boolean isPinned,
        Boolean isEdited,
        LocalDateTime createdAt,
        String slackPermalink,
        List<LinkPreviewDto> attachments,
        List<FileAttachmentDto> files) {
}
