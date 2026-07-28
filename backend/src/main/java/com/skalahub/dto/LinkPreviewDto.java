// 슬랙 링크 미리보기(attachments) 응답
package com.skalahub.dto;

public record LinkPreviewDto(
        String title,
        String titleLink,
        String text,
        String imageUrl,
        String fromUrl,
        String serviceName) {
}
