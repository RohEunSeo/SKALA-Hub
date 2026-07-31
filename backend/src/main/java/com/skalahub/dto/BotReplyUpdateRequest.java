// 관리자가 슬랙 봇 댓글 내용을 직접 수정할 때 쓰는 요청
package com.skalahub.dto;

public record BotReplyUpdateRequest(String content) {
}
