// 마이페이지 "계정 설정" 섹션 - 구글 계정 연동 상태
package com.skalahub.dto;

public record AccountLinkResponse(boolean googleLinked, String googleEmail) {
}
