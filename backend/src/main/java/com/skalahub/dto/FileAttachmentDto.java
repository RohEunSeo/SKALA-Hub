// 슬랙 첨부파일(files) 응답 - 이미지는 proxyUrl로 미리보기 제공
package com.skalahub.dto;

public record FileAttachmentDto(
        String name,
        String filetype,
        String mimetype,
        boolean isImage,
        Long sizeBytes,
        String proxyUrl) {
}
