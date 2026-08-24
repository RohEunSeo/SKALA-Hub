// 대시보드 반별·운영진 로그인(가입) 현황 - 정원 데이터가 없어 인원 수만 집계
package com.skalahub.dto;

public record DashboardLoginBarDto(String label, long count, boolean isStaff) {
}
