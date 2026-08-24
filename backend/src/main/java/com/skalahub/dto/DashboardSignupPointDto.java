// 대시보드 신규 가입자 추이 - 하루치 신규 가입자 수 + 그 날짜까지의 누적 가입자 수
package com.skalahub.dto;

import java.time.LocalDate;

public record DashboardSignupPointDto(LocalDate date, long newCount, long cumulative) {
}
