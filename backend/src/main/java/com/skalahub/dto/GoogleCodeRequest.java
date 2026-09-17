// 구글 계정 연동/로그인 - 프론트가 팝업(Google Identity Services)에서 받은 인가 코드
package com.skalahub.dto;

public record GoogleCodeRequest(String code) {
}
